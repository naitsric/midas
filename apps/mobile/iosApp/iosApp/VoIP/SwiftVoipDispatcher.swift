import Foundation
import ComposeApp

final class SwiftVoipDispatcher: NSObject, VoipCallDispatcher {
    private let callKitManager: CallKitManager

    init(callKitManager: CallKitManager) {
        self.callKitManager = callKitManager
        super.init()
    }

    func dial(toNumber: String, clientName: String?) {
        VoipDebugLog.shared.append(line: "[Voip] dial start to=\(toNumber)")
        Task {
            do {
                let callId = try await callKitManager.startOutgoingCall(
                    toNumber: toNumber, clientName: clientName
                )
                VoipDebugLog.shared.append(line: "[Voip] dial OK callId=\(callId)")
                MidasContext.shared.voipCallManager?.onDialed(callId: callId)
            } catch {
                VoipDebugLog.shared.append(line: "[Voip] dial FAILED: \(error)")
                MidasContext.shared.voipCallManager?.onCallEnded(failed: true)
            }
        }
    }

    func answer(callId: String) {
        // CallKit answer is driven by system UI via CXAnswerCallAction,
        // which CallKitManager handles in its CallKitProviderDelegate callback.
    }

    func hangup(callId: String) {
        NSLog("[Voip] hangup requested callId=\(callId)")
        Task {
            await callKitManager.endActiveCall()
        }
    }

    func setMuted(muted: Bool) {
        // Mute is driven by CXSetMutedCallAction. Direct toggle not exposed yet.
    }
}
