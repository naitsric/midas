/**
 * Call Control Lambda
 * Handles iOS app requests: /calls/dial, /calls/answer, /calls/hangup, /calls/token, /calls/active
 * Exposed via HTTP API Gateway.
 */

import { DynamoDBClient, PutItemCommand, UpdateItemCommand, QueryCommand, GetItemCommand } from '@aws-sdk/client-dynamodb';
import { ChimeSDKMeetingsClient, CreateMeetingCommand, CreateAttendeeCommand, DeleteMeetingCommand } from '@aws-sdk/client-chime-sdk-meetings';
import { ChimeSDKVoiceClient, CreateSipMediaApplicationCallCommand, UpdateSipMediaApplicationCallCommand } from '@aws-sdk/client-chime-sdk-voice';
import { SNSClient, PublishCommand } from '@aws-sdk/client-sns';
import { randomUUID } from 'crypto';

const dynamo = new DynamoDBClient({});
const chimeMeetings = new ChimeSDKMeetingsClient({});
const chimeVoice = new ChimeSDKVoiceClient({});
const sns = new SNSClient({});

const CALLS_TABLE = process.env.CALLS_TABLE!;
const SMA_ID = process.env.SMA_ID!;
const VOICE_CONNECTOR_ARN = process.env.VOICE_CONNECTOR_ARN!;
const VOIP_PUSH_TOPIC_ARN = process.env.VOIP_PUSH_TOPIC_ARN!;
const CHIME_OUTBOUND_NUMBER = process.env.CHIME_OUTBOUND_NUMBER!;
const MIDAS_BACKEND_URL = process.env.MIDAS_BACKEND_URL!;

interface ApiEvent {
  routeKey: string;
  body?: string;
  queryStringParameters?: Record<string, string>;
  headers: Record<string, string>;
  requestContext: {
    http: { method: string; path: string };
  };
}

interface ApiResponse {
  statusCode: number;
  headers: Record<string, string>;
  body: string;
}

export const handler = async (event: ApiEvent): Promise<ApiResponse> => {
  console.log('Request:', JSON.stringify(event, null, 2));

  const route = event.routeKey;
  const apiKey = event.headers['x-api-key'] || event.headers['X-API-Key'];

  if (!apiKey) {
    return json(401, { error: 'Missing X-API-Key header' });
  }

  // TODO: Validate API key against MIDAS backend and resolve advisorId
  const advisorId = apiKey; // Placeholder — replace with actual auth

  try {
    switch (route) {
      case 'POST /calls/dial':
        return await handleDial(event, advisorId, apiKey);

      case 'POST /calls/answer':
        return await handleAnswer(event, advisorId);

      case 'POST /calls/hangup':
        return await handleHangup(event, advisorId, apiKey);

      case 'GET /calls/token':
        return await handleGetToken(event, advisorId);

      case 'GET /calls/active':
        return await handleListActive(event, advisorId);

      case 'GET /calls/status':
        return await handleGetStatus(event, advisorId);

      default:
        return json(404, { error: 'Not found' });
    }
  } catch (error) {
    console.error('Handler error:', error);
    return json(500, { error: 'Internal server error' });
  }
};

/**
 * POST /calls/dial
 * Body: { toNumber: "+573111234567", clientName: "Juan Pérez" }
 *
 * Creates a Chime Meeting, attendees, and triggers SMA outbound call.
 */
async function handleDial(event: ApiEvent, advisorId: string, apiKey: string): Promise<ApiResponse> {
  const body = JSON.parse(event.body || '{}');
  const { toNumber, clientName } = body;

  if (!toNumber) {
    return json(400, { error: 'toNumber is required' });
  }

  const region = process.env.AWS_REGION || 'us-east-1';
  const requestToken = randomUUID();

  // 1. Create Chime Meeting
  const meeting = await chimeMeetings.send(new CreateMeetingCommand({
    ClientRequestToken: requestToken,
    MediaRegion: region,
    ExternalMeetingId: `outbound-${requestToken}`,
  }));

  const meetingId = meeting.Meeting!.MeetingId!;

  // 2. Create attendees: one for advisor (iOS), one for SMA (PSTN bridge)
  const advisorAttendee = await chimeMeetings.send(new CreateAttendeeCommand({
    MeetingId: meetingId,
    ExternalUserId: `advisor-${advisorId}`,
  }));

  const smaAttendee = await chimeMeetings.send(new CreateAttendeeCommand({
    MeetingId: meetingId,
    ExternalUserId: `sma-${requestToken}`,
  }));

  // 3. Trigger SMA outbound call — TransactionId is the canonical callId,
  //    so the SMA Handler can lookup by event.CallDetails.TransactionId.
  const smaCall = await chimeVoice.send(new CreateSipMediaApplicationCallCommand({
    SipMediaApplicationId: SMA_ID,
    FromPhoneNumber: CHIME_OUTBOUND_NUMBER,
    ToPhoneNumber: toNumber,
  }));

  const callId = smaCall.SipMediaApplicationCall!.TransactionId!;

  // 4. Store call state keyed by TransactionId (matches SMA event txId)
  const now = new Date().toISOString();
  const ttl = Math.floor(Date.now() / 1000) + 86400;

  await dynamo.send(new PutItemCommand({
    TableName: CALLS_TABLE,
    Item: {
      callId: { S: callId },
      advisorId: { S: advisorId },
      meetingId: { S: meetingId },
      toNumber: { S: toNumber },
      clientName: { S: clientName || 'Unknown' },
      direction: { S: 'outbound' },
      status: { S: 'dialing' },
      advisorAttendeeId: { S: advisorAttendee.Attendee!.AttendeeId! },
      smaAttendeeId: { S: smaAttendee.Attendee!.AttendeeId! },
      smaJoinToken: { S: smaAttendee.Attendee!.JoinToken! },
      createdAt: { S: now },
      ttl: { N: String(ttl) },
    },
  }));

  // Register the call in the MIDAS backend so it shows up in the dashboard
  // and is later linked with the recording / transcript / intent detection.
  // Failure here is non-fatal — the PSTN call still proceeds.
  try {
    await fetch(`${MIDAS_BACKEND_URL}/api/calls`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': apiKey,
      },
      body: JSON.stringify({
        client_name: clientName || toNumber,
        voip_call_id: callId,
      }),
    });
  } catch (err) {
    console.error('Failed to register call in MIDAS backend:', err);
  }

  const m = meeting.Meeting!;
  const a = advisorAttendee.Attendee!;
  return json(200, {
    callId,
    meetingId,
    joinToken: a.JoinToken!,
    attendeeId: a.AttendeeId!,
    externalUserId: a.ExternalUserId!,
    mediaRegion: m.MediaRegion!,
    audioHostUrl: m.MediaPlacement!.AudioHostUrl!,
    audioFallbackUrl: m.MediaPlacement!.AudioFallbackUrl!,
    signalingUrl: m.MediaPlacement!.SignalingUrl!,
    turnControlUrl: m.MediaPlacement!.TurnControlUrl!,
  });
}

/**
 * POST /calls/answer
 * Body: { callId: "uuid" }
 *
 * Advisor answered inbound call from iOS app. Updates SMA to bridge.
 */
async function handleAnswer(event: ApiEvent, advisorId: string): Promise<ApiResponse> {
  const body = JSON.parse(event.body || '{}');
  const { callId } = body;

  if (!callId) {
    return json(400, { error: 'callId is required' });
  }

  // Get call details
  const callData = await dynamo.send(new GetItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: callId } },
  }));

  if (!callData.Item) {
    return json(404, { error: 'Call not found' });
  }

  // Update status
  await dynamo.send(new UpdateItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: callId } },
    UpdateExpression: 'SET #s = :s, answeredAt = :t',
    ExpressionAttributeNames: { '#s': 'status' },
    ExpressionAttributeValues: {
      ':s': { S: 'active' },
      ':t': { S: new Date().toISOString() },
    },
  }));

  return json(200, {
    callId,
    meetingId: callData.Item.meetingId?.S,
    joinToken: callData.Item.joinToken?.S,
    status: 'active',
  });
}

/**
 * POST /calls/hangup
 * Body: { callId: "uuid" }
 */
async function handleHangup(event: ApiEvent, advisorId: string, _apiKey: string): Promise<ApiResponse> {
  const body = JSON.parse(event.body || '{}');
  const { callId } = body;

  if (!callId) {
    return json(400, { error: 'callId is required' });
  }

  const callData = await dynamo.send(new GetItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: callId } },
  }));

  if (!callData.Item) {
    return json(404, { error: 'Call not found' });
  }

  const meetingId = callData.Item.meetingId?.S;

  // Tell the SMA Lambda to hang up the PSTN leg. Without this, the call keeps
  // ringing the remote party even after the advisor cancels from the iOS app —
  // when the remote answers later, the meeting is gone and the bridge fails.
  try {
    await chimeVoice.send(new UpdateSipMediaApplicationCallCommand({
      SipMediaApplicationId: SMA_ID,
      TransactionId: callId,
      Arguments: { action: 'hangup' },
    }));
  } catch (err) {
    console.error('Failed to update SMA call (may already be ended):', err);
  }

  // Delete the Chime meeting (disconnects all participants)
  if (meetingId) {
    try {
      await chimeMeetings.send(new DeleteMeetingCommand({ MeetingId: meetingId }));
    } catch (err) {
      console.error('Failed to delete meeting:', err);
    }
  }

  // Update call status
  await dynamo.send(new UpdateItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: callId } },
    UpdateExpression: 'SET #s = :s, endedAt = :t',
    ExpressionAttributeNames: { '#s': 'status' },
    ExpressionAttributeValues: {
      ':s': { S: 'completed' },
      ':t': { S: new Date().toISOString() },
    },
  }));

  // Notify MIDAS backend so the dashboard call entry transitions to processing.
  // Recording Processor later sends /voip-recording with the audio metadata.
  try {
    await fetch(`${MIDAS_BACKEND_URL}/api/calls/voip-webhook`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        event: 'call_ended',
        callId,
        timestamp: new Date().toISOString(),
      }),
    });
  } catch (err) {
    console.error('Failed to notify backend of call end:', err);
  }

  return json(200, { callId, status: 'completed' });
}

/**
 * GET /calls/token?callId=uuid
 * Returns Chime meeting session config for iOS SDK to join.
 */
async function handleGetToken(event: ApiEvent, advisorId: string): Promise<ApiResponse> {
  const callId = event.queryStringParameters?.callId;

  if (!callId) {
    return json(400, { error: 'callId query parameter is required' });
  }

  const callData = await dynamo.send(new GetItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: callId } },
  }));

  if (!callData.Item) {
    return json(404, { error: 'Call not found' });
  }

  const meetingId = callData.Item.meetingId?.S;

  // Create a fresh attendee token for the advisor
  const attendee = await chimeMeetings.send(new CreateAttendeeCommand({
    MeetingId: meetingId!,
    ExternalUserId: `advisor-${advisorId}-${Date.now()}`,
  }));

  return json(200, {
    callId,
    meetingId,
    attendee: attendee.Attendee,
  });
}

/**
 * GET /calls/active
 * Lists active calls for the authenticated advisor.
 */
async function handleListActive(event: ApiEvent, advisorId: string): Promise<ApiResponse> {
  const result = await dynamo.send(new QueryCommand({
    TableName: CALLS_TABLE,
    IndexName: 'by-advisor',
    KeyConditionExpression: 'advisorId = :aid',
    FilterExpression: '#s IN (:s1, :s2, :s3)',
    ExpressionAttributeNames: { '#s': 'status' },
    ExpressionAttributeValues: {
      ':aid': { S: advisorId },
      ':s1': { S: 'ringing' },
      ':s2': { S: 'dialing' },
      ':s3': { S: 'active' },
    },
    ScanIndexForward: false,
    Limit: 20,
  }));

  const calls = (result.Items || []).map(item => ({
    callId: item.callId?.S,
    meetingId: item.meetingId?.S,
    direction: item.direction?.S,
    status: item.status?.S,
    toNumber: item.toNumber?.S,
    callerNumber: item.callerNumber?.S,
    clientName: item.clientName?.S,
    createdAt: item.createdAt?.S,
  }));

  return json(200, { calls });
}

/**
 * GET /calls/status?callId=uuid
 * Lightweight polling endpoint for the iOS app to detect when the PSTN
 * destination has answered (status transitions from "dialing" → "active").
 */
async function handleGetStatus(event: ApiEvent, _advisorId: string): Promise<ApiResponse> {
  const callId = event.queryStringParameters?.callId;
  if (!callId) {
    return json(400, { error: 'callId query parameter is required' });
  }

  const callData = await dynamo.send(new GetItemCommand({
    TableName: CALLS_TABLE,
    Key: { callId: { S: callId } },
  }));

  if (!callData.Item) {
    return json(404, { error: 'Call not found' });
  }

  return json(200, {
    callId,
    status: callData.Item.status?.S,
    meetingId: callData.Item.meetingId?.S,
  });
}

function json(statusCode: number, body: unknown): ApiResponse {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}
