import CallKit
import ComposeApp
import Foundation

struct ActiveCall {
    let uuid: UUID
    let callId: String
    let remoteHandle: String
    let meetingConfig: ChimeMeetingConfig
    let isOutgoing: Bool
    var statusPoller: Task<Void, Never>?
    var didJoinMeeting: Bool = false
    /// True once the Lambda confirmed CALL_ANSWERED; we still wait for CallKit
    /// to activate the audio session before starting the Chime meeting.
    var pendingMeetingStart: Bool = false
}

final class CallKitManager: NSObject {
    private let provider: CallKitProvider
    private let controller = CXCallController()
    private let chime: ChimeCallManager
    private let service: CallControlService
    private let ringback = RingbackPlayer()

    private var activeCalls: [UUID: ActiveCall] = [:]

    init(
        provider: CallKitProvider = CallKitProvider(),
        chime: ChimeCallManager = ChimeCallManager(),
        service: CallControlService
    ) {
        self.provider = provider
        self.chime = chime
        self.service = service
        super.init()
        self.provider.delegate = self
        self.chime.delegate = self
    }

    @discardableResult
    func startOutgoingCall(toNumber: String, clientName: String?) async throws -> String {
        let response = try await service.dial(toNumber: toNumber, clientName: clientName)
        let uuid = UUID()
        let config = Self.makeChimeConfig(from: response)
        let call = ActiveCall(
            uuid: uuid,
            callId: response.callId,
            remoteHandle: toNumber,
            meetingConfig: config,
            isOutgoing: true,
            didJoinMeeting: false,
            pendingMeetingStart: true
        )
        activeCalls[uuid] = call

        let handle = CXHandle(type: .phoneNumber, value: toNumber)
        let startAction = CXStartCallAction(call: uuid, handle: handle)
        startAction.contactIdentifier = clientName
        let transaction = CXTransaction(action: startAction)

        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            controller.request(transaction) { error in
                if let error { cont.resume(throwing: error) } else { cont.resume() }
            }
        }

        provider.reportOutgoingCallStartedConnecting(uuid: uuid)
        // Do NOT start Chime here. CallKit will activate the AVAudioSession
        // shortly via `provider(didActivate:)`; we start the meeting then so
        // Chime + CallKit don't fight for the audio session.
        return response.callId
    }

    /// Ends any active call (outgoing or answered). Used when the user taps "Colgar"
    /// from the Compose UI, which doesn't know the internal CallKit UUID.
    func endActiveCall() async {
        NSLog("[CallKit] endActiveCall, active=\(activeCalls.count)")
        guard let uuid = activeCalls.keys.first else {
            NSLog("[CallKit] no active call to end")
            return
        }
        do {
            try await endCall(uuid: uuid)
            NSLog("[CallKit] endCall succeeded")
        } catch {
            NSLog("[CallKit] endCall threw: \(error)")
            // CXEndCallAction can fail if CallKit thinks the call is gone.
            // Force-clean local state so the UI unblocks.
            if let call = activeCalls.removeValue(forKey: uuid) {
                if call.didJoinMeeting { chime.endMeeting() }
                Task { try? await service.hangup(callId: call.callId) }
            }
        }
    }

    func handleIncomingCall(
        callId: String,
        callerNumber: String,
        callerName: String?,
        meetingConfig: ChimeMeetingConfig,
        completion: @escaping (Error?) -> Void
    ) {
        let uuid = UUID()
        let call = ActiveCall(
            uuid: uuid,
            callId: callId,
            remoteHandle: callerNumber,
            meetingConfig: meetingConfig,
            isOutgoing: false
        )
        activeCalls[uuid] = call
        provider.reportIncomingCall(
            uuid: uuid,
            handle: callerNumber,
            displayName: callerName,
            completion: completion
        )
    }

    func endCall(uuid: UUID) async throws {
        let action = CXEndCallAction(call: uuid)
        let transaction = CXTransaction(action: action)
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            controller.request(transaction) { error in
                if let error { cont.resume(throwing: error) } else { cont.resume() }
            }
        }
    }

    private static func makeChimeConfig(from response: DialResponse) -> ChimeMeetingConfig {
        ChimeMeetingConfig(
            meetingId: response.meetingId,
            externalMeetingId: nil,
            mediaRegion: response.mediaRegion,
            mediaPlacement: ChimeMediaPlacement(
                audioHostUrl: response.audioHostUrl,
                audioFallbackUrl: response.audioFallbackUrl,
                signalingUrl: response.signalingUrl,
                turnControlUrl: response.turnControlUrl
            ),
            attendeeId: response.attendeeId,
            externalUserId: response.externalUserId,
            joinToken: response.joinToken
        )
    }
}

extension CallKitManager: CallKitProviderDelegate {
    func callKitProviderDidAnswerCall(uuid: UUID) {
        guard var call = activeCalls[uuid] else { return }
        // For incoming calls we still need to ack the call control side here.
        // Meeting start is deferred to didActivate so the audio session is ready.
        call.pendingMeetingStart = true
        activeCalls[uuid] = call
        Task {
            do {
                try await service.answer(callId: call.callId)
            } catch {
                provider.reportCallEnded(uuid: uuid, reason: .failed)
                activeCalls.removeValue(forKey: uuid)
            }
        }
    }

    func callKitProviderDidEndCall(uuid: UUID) {
        guard let call = activeCalls.removeValue(forKey: uuid) else { return }
        call.statusPoller?.cancel()
        ringback.stop()
        chime.endMeeting()
        Task { try? await service.hangup(callId: call.callId) }
        MidasContext.shared.voipCallManager?.onCallEnded(failed: false)
    }

    func callKitProviderDidActivateAudioSession() {
        VoipDebugLog.shared.append(line: "[CallKit] audio session activated")
        // CallKit owns the AVAudioSession now. Start any pending Chime meeting
        // on the main thread so AmazonChimeSDK doesn't trip the main-thread checker.
        for (uuid, var call) in activeCalls where call.pendingMeetingStart {
            call.pendingMeetingStart = false
            call.didJoinMeeting = true
            activeCalls[uuid] = call
            // For outgoing calls, play local ringback while waiting for the
            // PSTN side to bridge into the meeting (stopped on remote attendee join).
            if call.isOutgoing { ringback.start() }
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                do {
                    try self.chime.startMeeting(config: call.meetingConfig)
                } catch {
                    VoipDebugLog.shared.append(line: "[CallKit] startMeeting failed: \(error)")
                    self.ringback.stop()
                    self.provider.reportCallEnded(uuid: uuid, reason: .failed)
                    self.activeCalls.removeValue(forKey: uuid)
                }
            }
        }
    }

    func callKitProviderDidDeactivateAudioSession() {
        VoipDebugLog.shared.append(line: "[CallKit] audio session deactivated")
    }

    func callKitProviderDidSetMuted(uuid: UUID, muted: Bool) {
        chime.setMuted(muted)
    }
}

extension CallKitManager: ChimeCallManagerDelegate {
    func chimeCallDidConnect() {
        VoipDebugLog.shared.append(line: "[Chime] audio session started -> reportConnected")
        guard let call = activeCalls.values.first(where: { $0.isOutgoing }) else { return }
        provider.reportOutgoingCallConnected(uuid: call.uuid)
    }

    func chimeCallDidDisconnect(error: Error?) {
        VoipDebugLog.shared.append(line: "[Chime] audio session stopped error=\(String(describing: error))")
        ringback.stop()
        for (uuid, _) in activeCalls {
            provider.reportCallEnded(uuid: uuid, reason: error == nil ? .remoteEnded : .failed)
        }
        activeCalls.removeAll()
        MidasContext.shared.voipCallManager?.onCallEnded(failed: error != nil)
    }

    func chimeCallRemoteAttendeeJoined(attendeeId: String) {
        VoipDebugLog.shared.append(line: "[Chime] remote attendee joined \(attendeeId)")
        // PSTN bridged into the meeting → silence local ringback, real audio takes over.
        ringback.stop()
        guard let call = activeCalls.values.first(where: { $0.isOutgoing }) else { return }
        MidasContext.shared.voipCallManager?.onCallConnected(callId: call.callId)
    }

    func chimeCallRemoteAttendeeLeft(attendeeId: String) {}
}
