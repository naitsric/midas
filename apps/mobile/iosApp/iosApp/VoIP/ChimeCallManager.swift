import AmazonChimeSDK
import AmazonChimeSDKMedia
import AVFoundation
import Foundation

struct ChimeMeetingConfig {
    let meetingId: String
    let externalMeetingId: String?
    let mediaRegion: String
    let mediaPlacement: ChimeMediaPlacement
    let attendeeId: String
    let externalUserId: String
    let joinToken: String
}

struct ChimeMediaPlacement {
    let audioHostUrl: String
    let audioFallbackUrl: String
    let signalingUrl: String
    let turnControlUrl: String
}

protocol ChimeCallManagerDelegate: AnyObject {
    func chimeCallDidConnect()
    func chimeCallDidDisconnect(error: Error?)
    func chimeCallRemoteAttendeeJoined(attendeeId: String)
    func chimeCallRemoteAttendeeLeft(attendeeId: String)
}

final class ChimeCallManager: NSObject {
    weak var delegate: ChimeCallManagerDelegate?

    private var meetingSession: MeetingSession?
    private let logger = ConsoleLogger(name: "MidasChimeCallManager")

    func startMeeting(config: ChimeMeetingConfig) throws {
        let meeting = Meeting(
            externalMeetingId: config.externalMeetingId,
            mediaPlacement: MediaPlacement(
                audioFallbackUrl: config.mediaPlacement.audioFallbackUrl,
                audioHostUrl: config.mediaPlacement.audioHostUrl,
                signalingUrl: config.mediaPlacement.signalingUrl,
                turnControlUrl: config.mediaPlacement.turnControlUrl
            ),
            mediaRegion: config.mediaRegion,
            meetingId: config.meetingId
        )
        let attendee = Attendee(
            attendeeId: config.attendeeId,
            externalUserId: config.externalUserId,
            joinToken: config.joinToken
        )

        let createMeetingResponse = CreateMeetingResponse(meeting: meeting)
        let createAttendeeResponse = CreateAttendeeResponse(attendee: attendee)
        let configuration = MeetingSessionConfiguration(
            createMeetingResponse: createMeetingResponse,
            createAttendeeResponse: createAttendeeResponse
        )

        let session = DefaultMeetingSession(configuration: configuration, logger: logger)
        self.meetingSession = session

        session.audioVideo.addAudioVideoObserver(observer: self)
        session.audioVideo.addRealtimeObserver(observer: self)

        // callKitEnabled: true → Chime won't try to manage AVAudioSession itself.
        // Without this, Chime fights CallKit for the session and the mic device
        // silently fails after ~30s with `audioInputDeviceNotResponding`.
        let avConfig = AudioVideoConfiguration(callKitEnabled: true)
        try session.audioVideo.start(audioVideoConfiguration: avConfig)
    }

    func endMeeting() {
        meetingSession?.audioVideo.stop()
        meetingSession = nil
    }

    func setMuted(_ muted: Bool) {
        if muted {
            _ = meetingSession?.audioVideo.realtimeLocalMute()
        } else {
            _ = meetingSession?.audioVideo.realtimeLocalUnmute()
        }
    }
}

extension ChimeCallManager: AudioVideoObserver {
    func audioSessionDidStartConnecting(reconnecting: Bool) {}
    func audioSessionDidStart(reconnecting: Bool) {
        delegate?.chimeCallDidConnect()
    }
    func audioSessionDidDrop() {}
    func audioSessionDidStopWithStatus(sessionStatus: MeetingSessionStatus) {
        let error = sessionStatus.statusCode != .ok
            ? NSError(domain: "ChimeCallManager", code: Int(sessionStatus.statusCode.rawValue))
            : nil
        delegate?.chimeCallDidDisconnect(error: error)
    }
    func audioSessionDidCancelReconnect() {}
    func connectionDidRecover() {}
    func connectionDidBecomePoor() {}
    func videoSessionDidStartConnecting() {}
    func videoSessionDidStartWithStatus(sessionStatus: MeetingSessionStatus) {}
    func videoSessionDidStopWithStatus(sessionStatus: MeetingSessionStatus) {}
    func remoteVideoSourcesDidBecomeAvailable(sources: [RemoteVideoSource]) {}
    func remoteVideoSourcesDidBecomeUnavailable(sources: [RemoteVideoSource]) {}
    func cameraSendAvailabilityDidChange(available: Bool) {}
}

extension ChimeCallManager: RealtimeObserver {
    func volumeDidChange(volumeUpdates: [VolumeUpdate]) {}
    func signalStrengthDidChange(signalUpdates: [SignalUpdate]) {}
    func attendeesDidJoin(attendeeInfo: [AttendeeInfo]) {
        attendeeInfo.forEach { delegate?.chimeCallRemoteAttendeeJoined(attendeeId: $0.attendeeId) }
    }
    func attendeesDidLeave(attendeeInfo: [AttendeeInfo]) {
        attendeeInfo.forEach { delegate?.chimeCallRemoteAttendeeLeft(attendeeId: $0.attendeeId) }
    }
    func attendeesDidDrop(attendeeInfo: [AttendeeInfo]) {
        attendeeInfo.forEach { delegate?.chimeCallRemoteAttendeeLeft(attendeeId: $0.attendeeId) }
    }
    func attendeesDidMute(attendeeInfo: [AttendeeInfo]) {}
    func attendeesDidUnmute(attendeeInfo: [AttendeeInfo]) {}
}
