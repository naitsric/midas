/**
 * Recording Processor Lambda
 * Triggered by S3 event when a call recording is uploaded.
 * Downloads recording metadata, updates DynamoDB, and sends to MIDAS backend for transcription.
 */

import { DynamoDBClient, UpdateItemCommand, QueryCommand } from '@aws-sdk/client-dynamodb';
import { S3Client, GetObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';

const dynamo = new DynamoDBClient({});
const s3 = new S3Client({});

const CALLS_TABLE = process.env.CALLS_TABLE!;
const MIDAS_BACKEND_URL = process.env.MIDAS_BACKEND_URL!;

interface S3Event {
  Records: Array<{
    s3: {
      bucket: { name: string };
      object: { key: string; size: number };
    };
    eventTime: string;
  }>;
}

export const handler = async (event: S3Event): Promise<void> => {
  console.log('S3 Event:', JSON.stringify(event, null, 2));

  for (const record of event.Records) {
    const bucket = record.s3.bucket.name;
    const key = record.s3.object.key;
    const size = record.s3.object.size;

    console.log(`Processing recording: s3://${bucket}/${key} (${size} bytes)`);

    // Extract callId from S3 key
    // Expected format: recordings/{callId}/audio.wav or Amazon-Chime-SMA-Call-Recordings/{txId}/...
    const callId = extractCallId(key);

    if (!callId) {
      console.warn(`Could not extract callId from key: ${key}`);
      continue;
    }

    try {
      // Get recording metadata
      const headResult = await s3.send(new HeadObjectCommand({ Bucket: bucket, Key: key }));
      const contentType = headResult.ContentType || 'audio/wav';
      const durationEstimate = estimateDuration(size, contentType);

      // Generate pre-signed URL for backend to download (valid 1 hour)
      const presignedUrl = await getSignedUrl(
        s3,
        new GetObjectCommand({ Bucket: bucket, Key: key }),
        { expiresIn: 3600 },
      );

      // Update call record in DynamoDB
      await dynamo.send(new UpdateItemCommand({
        TableName: CALLS_TABLE,
        Key: { callId: { S: callId } },
        UpdateExpression: 'SET recordingKey = :k, recordingBucket = :b, recordingSize = :sz, durationSeconds = :d, #s = :s',
        ExpressionAttributeNames: { '#s': 'status' },
        ExpressionAttributeValues: {
          ':k': { S: key },
          ':b': { S: bucket },
          ':sz': { N: String(size) },
          ':d': { N: String(durationEstimate) },
          ':s': { S: 'recorded' },
        },
      }));

      // Send recording to MIDAS backend for transcription + intent detection
      const response = await fetch(`${MIDAS_BACKEND_URL}/api/calls/voip-recording`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          callId,
          recordingUrl: presignedUrl,
          bucket,
          key,
          size,
          durationSeconds: durationEstimate,
          contentType,
          timestamp: new Date().toISOString(),
        }),
      });

      if (!response.ok) {
        console.error(`Backend returned ${response.status}: ${await response.text()}`);
      } else {
        console.log(`Successfully sent recording ${callId} to backend`);

        // Update status to processing
        await dynamo.send(new UpdateItemCommand({
          TableName: CALLS_TABLE,
          Key: { callId: { S: callId } },
          UpdateExpression: 'SET #s = :s',
          ExpressionAttributeNames: { '#s': 'status' },
          ExpressionAttributeValues: {
            ':s': { S: 'processing' },
          },
        }));
      }
    } catch (error) {
      console.error(`Error processing recording ${callId}:`, error);

      // Mark as failed
      await dynamo.send(new UpdateItemCommand({
        TableName: CALLS_TABLE,
        Key: { callId: { S: callId } },
        UpdateExpression: 'SET #s = :s, errorMessage = :e',
        ExpressionAttributeNames: { '#s': 'status' },
        ExpressionAttributeValues: {
          ':s': { S: 'failed' },
          ':e': { S: String(error) },
        },
      }));
    }
  }
};

/**
 * Extract callId from S3 object key.
 * Handles formats:
 * - recordings/{callId}/audio.wav
 * - Amazon-Chime-SMA-Call-Recordings/{callId}/{timestamp}.wav
 */
function extractCallId(key: string): string | null {
  const parts = key.split('/');

  // recordings/{callId}/...
  if (parts[0] === 'recordings' && parts.length >= 2) {
    return parts[1];
  }

  // Amazon-Chime-SMA-Call-Recordings/{callId}/...
  if (parts[0] === 'Amazon-Chime-SMA-Call-Recordings' && parts.length >= 2) {
    return parts[1];
  }

  return null;
}

/**
 * Estimate call duration from file size.
 * WAV 16-bit stereo at 8kHz (Chime default) = ~32KB/s
 * WAV 16-bit stereo at 16kHz = ~64KB/s
 */
function estimateDuration(sizeBytes: number, contentType: string): number {
  // Assume 16-bit stereo at 8kHz (Chime SMA default)
  const bytesPerSecond = 32000;
  return Math.round(sizeBytes / bytesPerSecond);
}
