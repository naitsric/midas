/**
 * SMA Handler Lambda
 * Processes SIP Media Application events for PSTN call routing, recording, and bridging.
 *
 * Events handled:
 * - NEW_INBOUND_CALL: Incoming call from PSTN via Voice Connector
 * - NEW_OUTBOUND_CALL: Outbound call initiated by Call Control API
 * - CALL_ANSWERED: Far-end answered, bridge into Chime Meeting + start recording
 * - HANGUP: Call ended, stop recording + cleanup
 * - ACTION_SUCCESSFUL / ACTION_FAILED: Confirmation of SMA actions
 */

import { DynamoDBClient, PutItemCommand, UpdateItemCommand, GetItemCommand } from '@aws-sdk/client-dynamodb';
import { SNSClient, PublishCommand } from '@aws-sdk/client-sns';
import { ChimeSDKMeetingsClient, CreateMeetingCommand, CreateAttendeeCommand } from '@aws-sdk/client-chime-sdk-meetings';

const dynamo = new DynamoDBClient({});
const sns = new SNSClient({});
const chime = new ChimeSDKMeetingsClient({});

const RECORDINGS_BUCKET = process.env.RECORDINGS_BUCKET!;
const CALLS_TABLE = process.env.CALLS_TABLE!;
const VOIP_PUSH_TOPIC_ARN = process.env.VOIP_PUSH_TOPIC_ARN!;
const MIDAS_BACKEND_URL = process.env.MIDAS_BACKEND_URL!;

interface SmaEvent {
  SchemaVersion: string;
  Sequence: number;
  InvocationEventType: string;
  CallDetails: {
    TransactionId: string;
    Participants: Array<{
      CallId: string;
      ParticipantTag: string;
      To: string;
      From: string;
      Direction: string;
      Status: string;
    }>;
  };
  ActionData?: {
    Type: string;
    ErrorType?: string;
    ErrorMessage?: string;
  };
}

interface SmaAction {
  Type: string;
  Parameters: Record<string, unknown>;
}

export const handler = async (event: SmaEvent): Promise<{ SchemaVersion: string; Actions: SmaAction[] }> => {
  console.log('SMA Event:', JSON.stringify(event, null, 2));

  const txId = event.CallDetails.TransactionId;
  const eventType = event.InvocationEventType;

  try {
    switch (eventType) {
      case 'NEW_INBOUND_CALL':
        return await handleInboundCall(event, txId);

      case 'NEW_OUTBOUND_CALL':
        return await handleOutboundCall(event, txId);

      case 'CALL_ANSWERED':
        return await handleCallAnswered(event, txId);

      case 'HANGUP':
        return await handleHangup(event, txId);

      case 'CALL_UPDATE_REQUESTED': {
        // Triggered by call-control's UpdateSipMediaApplicationCall (cancel from iOS).
        const args = (event as any).ActionData?.Parameters?.Arguments;
        if (args?.action === 'hangup') {
          console.log('Hangup requested via UpdateSipMediaApplicationCall');
          return respond([hangupAction()]);
        }
        return respond([]);
      }

      case 'ACTION_SUCCESSFUL':
        console.log(`Action succeeded: ${event.ActionData?.Type}`);
        // After joining the Chime meeting, kick off recording. SMA only
        // processes one effectful action per Lambda response when JoinChimeMeeting
        // is involved, so we chain StartCallRecording here.
        if (event.ActionData?.Type === 'JoinChimeMeeting') {
          return respond([startRecordingAction(txId)]);
        }
        return respond([]);

      case 'ACTION_FAILED':
        console.error(`Action failed: ${event.ActionData?.Type} - ${event.ActionData?.ErrorMessage}`);
        // Only hang up if the failed action is critical to the call (e.g. JoinChimeMeeting).
        // StartCallRecording failures are non-fatal — the call should continue.
        if (event.ActionData?.Type === 'JoinChimeMeeting') {
          return respond([hangupAction()]);
        }
        return respond([]);

      default:
        console.log(`Unhandled event: ${eventType}`);
        return respond([]);
    }
  } catch (error) {
    console.error('Handler error:', error);
    return respond([hangupAction()]);
  }
};

async function handleInboundCall(event: SmaEvent, txId: string) {
  const caller = event.CallDetails.Participants[0];
  const callerNumber = caller.From;
  const calledNumber = caller.To;

  // Create a Chime meeting for this call
  const meeting = await chime.send(new CreateMeetingCommand({
    ClientRequestToken: txId,
    MediaRegion: process.env.AWS_REGION || 'us-east-1',
    ExternalMeetingId: `inbound-${txId}`,
  }));

  const meetingId = meeting.Meeting!.MeetingId!;

  // Create attendee for the SMA (PSTN leg)
  const smaAttendee = await chime.send(new CreateAttendeeCommand({
    MeetingId: meetingId,
    ExternalUserId: `sma-${txId}`,
  }));

  // Create attendee for the advisor (iOS app leg)
  const advisorAttendee = await chime.send(new CreateAttendeeCommand({
    MeetingId: meetingId,
    ExternalUserId: `advisor-${txId}`,
  }));

  // Look up advisor by called number (the Telnyx number assigned to them)
  // For now, store the call and send push to topic
  const now = new Date().toISOString();
  const ttl = Math.floor(Date.now() / 1000) + 86400; // 24h TTL

  await dynamo.send(new PutItemCommand({
    TableName: CALLS_TABLE,
    Item: {
      callId: { S: txId },
      meetingId: { S: meetingId },
      callerNumber: { S: callerNumber },
      calledNumber: { S: calledNumber },
      direction: { S: 'inbound' },
      status: { S: 'ringing' },
      smaAttendeeId: { S: smaAttendee.Attendee!.AttendeeId! },
      smaJoinToken: { S: smaAttendee.Attendee!.JoinToken! },
      advisorAttendeeId: { S: advisorAttendee.Attendee!.AttendeeId! },
      joinToken: { S: advisorAttendee.Attendee!.JoinToken! },
      createdAt: { S: now },
      ttl: { N: String(ttl) },
    },
  }));

  // Send VoIP push notification to advisor's device
  await sns.send(new PublishCommand({
    TopicArn: VOIP_PUSH_TOPIC_ARN,
    Message: JSON.stringify({
      type: 'incoming_call',
      callId: txId,
      meetingId,
      callerNumber,
      joinToken: advisorAttendee.Attendee!.JoinToken!,
    }),
    MessageAttributes: {
      event: { DataType: 'String', StringValue: 'incoming_call' },
    },
  }));

  // Pause for 30s while waiting for advisor to answer via iOS app
  return respond([
    {
      Type: 'Pause',
      Parameters: { DurationInMilliseconds: '30000' },
    },
  ]);
}

async function handleOutboundCall(_event: SmaEvent, _txId: string) {
  // CreateSipMediaApplicationCall already dialed the PSTN destination.
  // Wait for CALL_ANSWERED — no action to perform yet.
  return respond([]);
}

async function handleCallAnswered(event: SmaEvent, txId: string) {
  // Far-end answered — join the Chime meeting and start recording
  const callData = await dynamo.send(new GetItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: txId } },
  }));

  const meetingId = callData.Item?.meetingId?.S;

  await dynamo.send(new UpdateItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: txId } },
    UpdateExpression: 'SET #s = :s, answeredAt = :t',
    ExpressionAttributeNames: { '#s': 'status' },
    ExpressionAttributeValues: {
      ':s': { S: 'active' },
      ':t': { S: new Date().toISOString() },
    },
  }));

  const actions: SmaAction[] = [];

  // Join Chime Meeting first. StartCallRecording is chained when
  // ACTION_SUCCESSFUL fires for JoinChimeMeeting (see handler switch).
  const smaJoinToken = callData.Item?.smaJoinToken?.S;
  if (meetingId && smaJoinToken) {
    actions.push({
      Type: 'JoinChimeMeeting',
      Parameters: {
        JoinToken: smaJoinToken,
        MeetingId: meetingId,
      },
    });
  } else {
    // No meeting to join — start recording immediately on the PSTN leg.
    actions.push(startRecordingAction(txId));
  }

  return respond(actions);
}

function startRecordingAction(txId: string): SmaAction {
  return {
    Type: 'StartCallRecording',
    Parameters: {
      Track: 'BOTH',
      Destination: {
        Type: 'S3',
        Location: `s3://${RECORDINGS_BUCKET}/recordings/${txId}`,
      },
    },
  };
}

async function handleHangup(event: SmaEvent, txId: string) {
  // Update call status in DynamoDB
  await dynamo.send(new UpdateItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: txId } },
    UpdateExpression: 'SET #s = :s, endedAt = :t',
    ExpressionAttributeNames: { '#s': 'status' },
    ExpressionAttributeValues: {
      ':s': { S: 'completed' },
      ':t': { S: new Date().toISOString() },
    },
  }));

  // Notify MIDAS backend that call ended
  try {
    await fetch(`${MIDAS_BACKEND_URL}/api/calls/voip-webhook`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        event: 'call_ended',
        callId: txId,
        timestamp: new Date().toISOString(),
      }),
    });
  } catch (err) {
    console.error('Failed to notify backend:', err);
  }

  return respond([
    {
      Type: 'StopCallRecording',
      Parameters: {},
    },
  ]);
}

function hangupAction(): SmaAction {
  return { Type: 'Hangup', Parameters: { SipResponseCode: '480' } };
}

function respond(actions: SmaAction[]) {
  return {
    SchemaVersion: '1.0',
    Actions: actions,
  };
}
