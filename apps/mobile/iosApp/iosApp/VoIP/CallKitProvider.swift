import CallKit
import Foundation
import AVFoundation

protocol CallKitProviderDelegate: AnyObject {
    func callKitProviderDidAnswerCall(uuid: UUID)
    func callKitProviderDidEndCall(uuid: UUID)
    func callKitProviderDidActivateAudioSession()
    func callKitProviderDidDeactivateAudioSession()
    func callKitProviderDidSetMuted(uuid: UUID, muted: Bool)
}

final class CallKitProvider: NSObject {
    weak var delegate: CallKitProviderDelegate?

    let provider: CXProvider

    override init() {
        let configuration = CXProviderConfiguration()
        configuration.supportsVideo = false
        configuration.maximumCallsPerCallGroup = 1
        configuration.maximumCallGroups = 1
        configuration.supportedHandleTypes = [.phoneNumber, .generic]
        configuration.includesCallsInRecents = true
        self.provider = CXProvider(configuration: configuration)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    func reportIncomingCall(
        uuid: UUID,
        handle: String,
        displayName: String?,
        completion: @escaping (Error?) -> Void
    ) {
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: handle)
        update.localizedCallerName = displayName
        update.hasVideo = false
        update.supportsHolding = false
        update.supportsDTMF = true
        provider.reportNewIncomingCall(with: uuid, update: update, completion: completion)
    }

    func reportOutgoingCallStartedConnecting(uuid: UUID, at date: Date = Date()) {
        provider.reportOutgoingCall(with: uuid, startedConnectingAt: date)
    }

    func reportOutgoingCallConnected(uuid: UUID, at date: Date = Date()) {
        provider.reportOutgoingCall(with: uuid, connectedAt: date)
    }

    func reportCallEnded(uuid: UUID, reason: CXCallEndedReason) {
        provider.reportCall(with: uuid, endedAt: Date(), reason: reason)
    }
}

extension CallKitProvider: CXProviderDelegate {
    func providerDidReset(_ provider: CXProvider) {}

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        configureAudioSession()
        delegate?.callKitProviderDidAnswerCall(uuid: action.callUUID)
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        delegate?.callKitProviderDidEndCall(uuid: action.callUUID)
        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        configureAudioSession()
        action.fulfill()
    }

    /// Set the audio session category required for VoIP, but DON'T activate it —
    /// CallKit calls setActive(true) for us right before invoking `didActivate`.
    /// Activating it here would race CallKit and trigger the audio session conflicts
    /// that show up in Chime as `audioInputDeviceNotResponding`.
    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(
                .playAndRecord,
                mode: .voiceChat,
                options: [.allowBluetooth, .allowBluetoothA2DP, .defaultToSpeaker]
            )
            try session.setPreferredSampleRate(48000)
            try session.setPreferredIOBufferDuration(0.02)
        } catch {
            NSLog("[CallKit] failed to configure audio session: \(error)")
        }
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        delegate?.callKitProviderDidSetMuted(uuid: action.callUUID, muted: action.isMuted)
        action.fulfill()
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        delegate?.callKitProviderDidActivateAudioSession()
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        delegate?.callKitProviderDidDeactivateAudioSession()
    }
}
