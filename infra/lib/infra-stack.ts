import * as cdk from 'aws-cdk-lib/core';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import { NodejsFunction } from 'aws-cdk-lib/aws-lambda-nodejs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as apigateway from 'aws-cdk-lib/aws-apigatewayv2';
import * as sns from 'aws-cdk-lib/aws-sns';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as s3n from 'aws-cdk-lib/aws-s3-notifications';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecsPatterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as ecrAssets from 'aws-cdk-lib/aws-ecr-assets';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import { Construct } from 'constructs';
import * as path from 'path';
import {
  ChimePhoneNumber,
  ChimeSipMediaApp,
  ChimeSipRule,
  ChimeVoiceConnector,
  PhoneCountry,
  PhoneNumberType,
  PhoneProductType,
  TriggerType,
  Protocol,
} from 'cdk-amazon-chime-resources';

interface MidasVoipStackProps extends cdk.StackProps {
  /** Telnyx SIP trunk outbound hostname (e.g. sip.telnyx.com) */
  telnyxSipHost?: string;
  /** MIDAS backend URL for post-call webhooks */
  midasBackendUrl?: string;
}

export class InfraStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: MidasVoipStackProps) {
    super(scope, id, props);

    const telnyxSipHost = props?.telnyxSipHost ?? 'sip.telnyx.com';
    const midasBackendUrl = props?.midasBackendUrl ?? 'https://api.midas.com';

    // ─── S3: Call Recordings ───────────────────────────────────────
    const recordingsBucket = new s3.Bucket(this, 'CallRecordingsBucket', {
      bucketName: `midas-call-recordings-${this.account}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      lifecycleRules: [
        { expiration: cdk.Duration.days(90), id: 'expire-after-90-days' },
      ],
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // Grant Chime access to write recordings
    recordingsBucket.addToResourcePolicy(
      new iam.PolicyStatement({
        effect: iam.Effect.ALLOW,
        principals: [new iam.ServicePrincipal('voiceconnector.chime.amazonaws.com')],
        actions: ['s3:PutObject', 's3:PutObjectAcl'],
        resources: [recordingsBucket.arnForObjects('*')],
      }),
    );

    // ─── DynamoDB: Active Calls State ──────────────────────────────
    const callsTable = new dynamodb.Table(this, 'ActiveCallsTable', {
      tableName: 'midas-active-calls',
      partitionKey: { name: 'callId', type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      timeToLiveAttribute: 'ttl',
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    callsTable.addGlobalSecondaryIndex({
      indexName: 'by-advisor',
      partitionKey: { name: 'advisorId', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'createdAt', type: dynamodb.AttributeType.STRING },
    });

    callsTable.addGlobalSecondaryIndex({
      indexName: 'by-meeting',
      partitionKey: { name: 'meetingId', type: dynamodb.AttributeType.STRING },
    });

    // ─── SNS: VoIP Push Notifications ──────────────────────────────
    const voipPushTopic = new sns.Topic(this, 'VoipPushTopic', {
      topicName: 'midas-voip-push',
      displayName: 'MIDAS VoIP Push Notifications',
    });

    // APNs VoIP Token Auth credentials (.p8). Placeholders — actualizar en consola
    // tras generar el key en Apple Developer Portal. Ver infra/scripts/create-apns-voip-platform.sh
    const apnsVoipSecret = new secretsmanager.Secret(this, 'ApnsVoipSecret', {
      secretName: 'midas/apns-voip',
      description: 'APNs VoIP Token Auth (.p8): bundle_id, team_id, key_id, private_key',
      secretObjectValue: {
        bundle_id: cdk.SecretValue.unsafePlainText('com.cristianduque.midas'),
        team_id: cdk.SecretValue.unsafePlainText('PLACEHOLDER_TEAM_ID'),
        key_id: cdk.SecretValue.unsafePlainText('PLACEHOLDER_KEY_ID'),
        private_key: cdk.SecretValue.unsafePlainText('PLACEHOLDER_P8_CONTENT'),
      },
    });

    // ARN of the SNS Platform Application (APNS_VOIP). Creado por script tras cargar .p8.
    const apnsVoipPlatformArnParam = new ssm.StringParameter(this, 'ApnsVoipPlatformArn', {
      parameterName: '/midas/apns-voip/platform-app-arn',
      stringValue: 'PLACEHOLDER_ARN',
      description: 'Run infra/scripts/create-apns-voip-platform.sh after uploading .p8 to midas/apns-voip',
    });

    // ─── Lambda: SMA Handler ───────────────────────────────────────
    // Handles PSTN call events (inbound/outbound routing, recording, bridging)
    const smaHandler = new NodejsFunction(this, 'SmaHandler', {
      functionName: 'midas-sma-handler',
      runtime: lambda.Runtime.NODEJS_20_X,
      entry: path.join(__dirname, '..', 'lambda', 'sma-handler', 'index.ts'),
      handler: 'handler',
      timeout: cdk.Duration.seconds(30),
      environment: {
        RECORDINGS_BUCKET: recordingsBucket.bucketName,
        CALLS_TABLE: callsTable.tableName,
        VOIP_PUSH_TOPIC_ARN: voipPushTopic.topicArn,
        MIDAS_BACKEND_URL: midasBackendUrl,
      },
    });

    callsTable.grantReadWriteData(smaHandler);
    voipPushTopic.grantPublish(smaHandler);
    smaHandler.addToRolePolicy(
      new iam.PolicyStatement({
        actions: [
          'chime:CreateMeeting',
          'chime:CreateAttendee',
          'chime:DeleteMeeting',
        ],
        resources: ['*'],
      }),
    );

    // ─── Lambda: Call Control API ──────────────────────────────────
    // Handles iOS app requests: /dial, /answer, /hangup, /status
    const callControlHandler = new NodejsFunction(this, 'CallControlHandler', {
      functionName: 'midas-call-control',
      runtime: lambda.Runtime.NODEJS_20_X,
      entry: path.join(__dirname, '..', 'lambda', 'call-control', 'index.ts'),
      handler: 'handler',
      timeout: cdk.Duration.seconds(30),
      environment: {
        CALLS_TABLE: callsTable.tableName,
        SMA_ID: '', // Set after SMA creation
        VOIP_PUSH_TOPIC_ARN: voipPushTopic.topicArn,
        MIDAS_BACKEND_URL: midasBackendUrl,
      },
    });

    callsTable.grantReadWriteData(callControlHandler);
    voipPushTopic.grantPublish(callControlHandler);
    callControlHandler.addToRolePolicy(
      new iam.PolicyStatement({
        actions: [
          'chime:CreateMeeting',
          'chime:CreateAttendee',
          'chime:DeleteMeeting',
          'chime:CreateSipMediaApplicationCall',
          'chime:UpdateSipMediaApplication',
          'chime:UpdateSipMediaApplicationCall',
        ],
        resources: ['*'],
      }),
    );

    // ─── Lambda: Recording Processor ───────────────────────────────
    // Triggered by S3 event when recording is uploaded, sends to MIDAS backend
    const recordingProcessor = new NodejsFunction(this, 'RecordingProcessor', {
      functionName: 'midas-recording-processor',
      runtime: lambda.Runtime.NODEJS_20_X,
      entry: path.join(__dirname, '..', 'lambda', 'recording-processor', 'index.ts'),
      handler: 'handler',
      timeout: cdk.Duration.minutes(5),
      memorySize: 512,
      environment: {
        CALLS_TABLE: callsTable.tableName,
        MIDAS_BACKEND_URL: midasBackendUrl,
      },
    });

    recordingsBucket.grantRead(recordingProcessor);
    callsTable.grantReadWriteData(recordingProcessor);

    // S3 event → Lambda when recording is uploaded.
    // SMA writes to `recordings/{txId}/...` per the StartCallRecording action.
    recordingsBucket.addEventNotification(
      s3.EventType.OBJECT_CREATED,
      new s3n.LambdaDestination(recordingProcessor),
      { prefix: 'recordings/' },
    );

    // ─── Chime: Voice Connector (SIP Trunk to Telnyx) ──────────────
    const voiceConnector = new ChimeVoiceConnector(this, 'VoiceConnector', {
      region: this.region,
      name: 'midas-telnyx-trunk',
      encryption: false, // Telnyx supports TLS but start without for simplicity
      origination: [
        {
          host: telnyxSipHost,
          port: 5060,
          protocol: Protocol.UDP,
          priority: 1,
          weight: 1,
        },
      ],
      termination: {
        callingRegions: ['CO', 'MX', 'US'],
        cps: 1,
        // Telnyx SIP signaling IPs across PoPs (required for Latency AnchorSite)
        // Source: https://support.telnyx.com/en/articles/6066053
        terminationCidrs: [
          '192.76.120.0/27',     // Chicago (covers .0-.31)
          '147.75.65.128/27',    // Ashburn (covers .128-.159)
          '185.86.151.0/27',     // Amsterdam (covers .0-.31)
          '103.196.170.0/27',    // Sydney (covers .0-.31)
          '170.249.224.0/27',    // Frankfurt (covers .0-.31)
        ],
      },
    });

    // ─── Chime: SIP Media Application ──────────────────────────────
    const sipMediaApp = new ChimeSipMediaApp(this, 'SipMediaApp', {
      region: this.region,
      name: 'midas-sma',
      endpoint: smaHandler.functionArn,
    });

    // Update Call Control Lambda with SMA ID
    callControlHandler.addEnvironment('SMA_ID', sipMediaApp.sipMediaAppId);
    callControlHandler.addEnvironment('VOICE_CONNECTOR_ARN', voiceConnector.voiceConnectorId);

    // ─── Chime: Phone Number (inbound PSTN) ────────────────────────
    // Número Chime SDK nativo para inbound. Evita el problema de
    // "Disallowed calling number region" que sufre el path Telnyx→VC
    // con caller IDs no-US (validación interna de Chime no documentada).
    const inboundPhoneNumber = new ChimePhoneNumber(this, 'InboundPhoneNumber', {
      phoneProductType: PhoneProductType.SMA,
      phoneCountry: PhoneCountry.US,
      phoneNumberType: PhoneNumberType.LOCAL,
      phoneAreaCode: 352, // Florida
    });

    // Caller ID for outbound SMA calls — uses the provisioned Chime number.
    callControlHandler.addEnvironment('CHIME_OUTBOUND_NUMBER', inboundPhoneNumber.phoneNumber);

    // ─── Chime: SIP Rule ───────────────────────────────────────────
    // Routes incoming PSTN calls to the Chime number → SMA Lambda.
    new ChimeSipRule(this, 'SipRuleByNumber', {
      name: 'midas-inbound-rule-by-number',
      triggerType: TriggerType.TO_PHONE_NUMBER,
      triggerValue: inboundPhoneNumber.phoneNumber,
      targetApplications: [
        {
          priority: 1,
          sipMediaApplicationId: sipMediaApp.sipMediaAppId,
          region: this.region,
        },
      ],
    });

    // ─── API Gateway: REST API for iOS App ─────────────────────────
    const api = new apigateway.CfnApi(this, 'CallControlApi', {
      name: 'midas-call-control-api',
      protocolType: 'HTTP',
      corsConfiguration: {
        allowMethods: ['POST', 'GET', 'OPTIONS'],
        allowOrigins: ['*'],
        allowHeaders: ['Content-Type', 'Authorization', 'X-API-Key'],
      },
    });

    const integration = new apigateway.CfnIntegration(this, 'CallControlIntegration', {
      apiId: api.ref,
      integrationType: 'AWS_PROXY',
      integrationUri: callControlHandler.functionArn,
      payloadFormatVersion: '2.0',
    });

    // Routes
    const routes = [
      { path: 'POST /calls/dial', description: 'Initiate outbound call' },
      { path: 'POST /calls/answer', description: 'Answer inbound call' },
      { path: 'POST /calls/hangup', description: 'Hangup active call' },
      { path: 'GET /calls/token', description: 'Get Chime meeting token for iOS' },
      { path: 'GET /calls/active', description: 'List active calls for advisor' },
      { path: 'GET /calls/status', description: 'Polling: get call status by id' },
    ];

    routes.forEach((route, i) => {
      new apigateway.CfnRoute(this, `Route${i}`, {
        apiId: api.ref,
        routeKey: route.path,
        target: `integrations/${integration.ref}`,
      });
    });

    const stage = new apigateway.CfnStage(this, 'ApiStage', {
      apiId: api.ref,
      stageName: '$default',
      autoDeploy: true,
    });

    // Grant API Gateway permission to invoke Lambda
    callControlHandler.addPermission('ApiGatewayInvoke', {
      principal: new iam.ServicePrincipal('apigateway.amazonaws.com'),
      sourceArn: `arn:aws:execute-api:${this.region}:${this.account}:${api.ref}/*`,
    });

    // ─── Fargate: FastAPI Backend ──────────────────────────────────
    // Secrets: DATABASE_URL (Supabase) + GEMINI_API_KEY
    // Create in console first, then reference by ARN to avoid CDK managing them.
    const backendSecrets = new secretsmanager.Secret(this, 'BackendSecrets', {
      secretName: 'midas/backend',
      description: 'FastAPI backend secrets (DATABASE_URL, GEMINI_API_KEY)',
      secretObjectValue: {
        DATABASE_URL: cdk.SecretValue.unsafePlainText('postgresql://placeholder'),
        GEMINI_API_KEY: cdk.SecretValue.unsafePlainText('placeholder'),
      },
    });

    const backendImage = new ecrAssets.DockerImageAsset(this, 'BackendImage', {
      directory: path.join(__dirname, '..', '..', 'apps', 'api-backend'),
      platform: ecrAssets.Platform.LINUX_AMD64,
    });

    const vpc = new ec2.Vpc(this, 'BackendVpc', {
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        { name: 'public', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
      ],
    });

    const cluster = new ecs.Cluster(this, 'BackendCluster', {
      clusterName: 'midas-backend',
      vpc,
    });

    const backendService = new ecsPatterns.ApplicationLoadBalancedFargateService(
      this,
      'BackendService',
      {
        cluster,
        cpu: 256,
        memoryLimitMiB: 512,
        desiredCount: 1,
        publicLoadBalancer: true,
        assignPublicIp: true,
        taskSubnets: { subnetType: ec2.SubnetType.PUBLIC },
        // ADK + google.genai imports add ~70s to cold start; give the task
        // room to come up before the ALB starts firing health checks.
        healthCheckGracePeriod: cdk.Duration.seconds(180),
        taskImageOptions: {
          image: ecs.ContainerImage.fromDockerImageAsset(backendImage),
          containerPort: 8000,
          environment: {
            SNS_VOIP_PLATFORM_APP_ARN_PARAM: apnsVoipPlatformArnParam.parameterName,
            AWS_DEFAULT_REGION: this.region,
          },
          secrets: {
            DATABASE_URL: ecs.Secret.fromSecretsManager(backendSecrets, 'DATABASE_URL'),
            GEMINI_API_KEY: ecs.Secret.fromSecretsManager(backendSecrets, 'GEMINI_API_KEY'),
          },
        },
      }
    );

    backendService.targetGroup.configureHealthCheck({
      path: '/health',
      healthyHttpCodes: '200',
      interval: cdk.Duration.seconds(30),
      timeout: cdk.Duration.seconds(10),
      healthyThresholdCount: 2,
      unhealthyThresholdCount: 5,
    });

    // Backend reads recording WAVs to generate fresh pre-signed URLs for the
    // mobile app's in-app audio player (the URL stored in DB expires in 1h).
    recordingsBucket.grantRead(backendService.taskDefinition.taskRole);
    backendService.taskDefinition.defaultContainer?.addEnvironment(
      'RECORDINGS_BUCKET',
      recordingsBucket.bucketName,
    );

    // Backend needs to register PushKit device tokens against the APNS_VOIP
    // PlatformApplication and publish to per-advisor endpoints.
    apnsVoipPlatformArnParam.grantRead(backendService.taskDefinition.taskRole);
    backendService.taskDefinition.taskRole.addToPrincipalPolicy(
      new iam.PolicyStatement({
        actions: [
          'sns:CreatePlatformEndpoint',
          'sns:SetEndpointAttributes',
          'sns:GetEndpointAttributes',
          'sns:DeleteEndpoint',
          'sns:Publish',
        ],
        resources: ['*'],
      }),
    );

    // Wire SMA Handler + Recording Processor + Call Control to real backend URL
    const backendUrl = `http://${backendService.loadBalancer.loadBalancerDnsName}`;
    smaHandler.addEnvironment('MIDAS_BACKEND_URL', backendUrl);
    recordingProcessor.addEnvironment('MIDAS_BACKEND_URL', backendUrl);
    callControlHandler.addEnvironment('MIDAS_BACKEND_URL', backendUrl);

    // ─── Outputs ───────────────────────────────────────────────────
    new cdk.CfnOutput(this, 'ApiUrl', {
      value: `https://${api.ref}.execute-api.${this.region}.amazonaws.com`,
      description: 'Call Control API URL (for iOS app)',
    });

    new cdk.CfnOutput(this, 'RecordingsBucketName', {
      value: recordingsBucket.bucketName,
      description: 'S3 bucket for call recordings',
    });

    new cdk.CfnOutput(this, 'SipMediaApplicationId', {
      value: sipMediaApp.sipMediaAppId,
      description: 'Chime SIP Media Application ID',
    });

    new cdk.CfnOutput(this, 'VoiceConnectorId', {
      value: voiceConnector.voiceConnectorId,
      description: 'Chime Voice Connector ID (configure in Telnyx)',
    });

    new cdk.CfnOutput(this, 'VoipPushTopicArn', {
      value: voipPushTopic.topicArn,
      description: 'SNS Topic ARN for VoIP push notifications',
    });

    new cdk.CfnOutput(this, 'InboundPhoneNumberOutput', {
      value: inboundPhoneNumber.phoneNumber,
      description: 'Chime SDK phone number for PSTN inbound (marca este número)',
    });

    new cdk.CfnOutput(this, 'BackendUrl', {
      value: `http://${backendService.loadBalancer.loadBalancerDnsName}`,
      description: 'FastAPI backend URL (ALB DNS)',
    });

    new cdk.CfnOutput(this, 'BackendSecretsArn', {
      value: backendSecrets.secretArn,
      description: 'Secrets Manager ARN — actualiza con DATABASE_URL real de Supabase',
    });

    new cdk.CfnOutput(this, 'ApnsVoipSecretArn', {
      value: apnsVoipSecret.secretArn,
      description: 'Secrets Manager ARN — sube tu .p8 + team_id + key_id + bundle_id',
    });

    new cdk.CfnOutput(this, 'ApnsVoipPlatformArnParam', {
      value: apnsVoipPlatformArnParam.parameterName,
      description: 'SSM Parameter — actualiza con ARN tras correr create-apns-voip-platform.sh',
    });
  }
}
