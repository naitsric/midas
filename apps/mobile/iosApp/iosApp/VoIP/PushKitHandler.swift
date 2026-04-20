import Foundation
import PushKit
import CallKit

protocol PushKitHandlerDelegate: AnyObject {
    func pushKitDidUpdateToken(_ token: String)
    func pushKitDidInvalidateToken()
    func pushKitDidReceiveIncomingCall(payload: IncomingCallPayload, completion: @escaping () -> Void)
}

struct IncomingCallPayload {
    let callId: String
    let callerNumber: String
    let callerName: String?
    let meetingConfig: ChimeMeetingConfig
}

final class PushKitHandler: NSObject {
    weak var delegate: PushKitHandlerDelegate?

    private let registry = PKPushRegistry(queue: .main)

    func start() {
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
    }

    var currentToken: String? {
        guard let data = registry.pushToken(for: .voIP) else { return nil }
        return data.map { String(format: "%02x", $0) }.joined()
    }
}

extension PushKitHandler: PKPushRegistryDelegate {
    func pushRegistry(
        _ registry: PKPushRegistry,
        didUpdate pushCredentials: PKPushCredentials,
        for type: PKPushType
    ) {
        guard type == .voIP else { return }
        let token = pushCredentials.token.map { String(format: "%02x", $0) }.joined()
        delegate?.pushKitDidUpdateToken(token)
    }

    func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        guard type == .voIP else { return }
        delegate?.pushKitDidInvalidateToken()
    }

    func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        guard type == .voIP else { completion(); return }
        guard let parsed = parsePayload(payload.dictionaryPayload) else {
            completion()
            return
        }
        delegate?.pushKitDidReceiveIncomingCall(payload: parsed, completion: completion)
    }

    private func parsePayload(_ dict: [AnyHashable: Any]) -> IncomingCallPayload? {
        guard
            let callId = dict["callId"] as? String,
            let callerNumber = dict["callerNumber"] as? String,
            let meeting = dict["meeting"] as? [String: Any],
            let attendee = dict["attendee"] as? [String: Any],
            let meetingId = meeting["meetingId"] as? String,
            let mediaRegion = meeting["mediaRegion"] as? String,
            let placement = meeting["mediaPlacement"] as? [String: Any],
            let audioHostUrl = placement["audioHostUrl"] as? String,
            let audioFallbackUrl = placement["audioFallbackUrl"] as? String,
            let signalingUrl = placement["signalingUrl"] as? String,
            let turnControlUrl = placement["turnControlUrl"] as? String,
            let attendeeId = attendee["attendeeId"] as? String,
            let externalUserId = attendee["externalUserId"] as? String,
            let joinToken = attendee["joinToken"] as? String
        else {
            return nil
        }

        let config = ChimeMeetingConfig(
            meetingId: meetingId,
            externalMeetingId: meeting["externalMeetingId"] as? String,
            mediaRegion: mediaRegion,
            mediaPlacement: ChimeMediaPlacement(
                audioHostUrl: audioHostUrl,
                audioFallbackUrl: audioFallbackUrl,
                signalingUrl: signalingUrl,
                turnControlUrl: turnControlUrl
            ),
            attendeeId: attendeeId,
            externalUserId: externalUserId,
            joinToken: joinToken
        )

        return IncomingCallPayload(
            callId: callId,
            callerNumber: callerNumber,
            callerName: dict["callerName"] as? String,
            meetingConfig: config
        )
    }
}
