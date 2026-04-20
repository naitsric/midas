import AVFoundation
import Foundation

/// Plays a local ringback tone while waiting for the PSTN leg to answer.
/// Chime SDK doesn't generate ringback (it expects early-media from the carrier),
/// so without this the user hears dead silence between dial and answer.
final class RingbackPlayer {
    private var player: AVAudioPlayer?

    func start() {
        guard player?.isPlaying != true else { return }
        guard let url = Bundle.main.url(forResource: "ringback", withExtension: "wav") else {
            NSLog("[Ringback] ringback.wav not found in bundle")
            return
        }
        do {
            let p = try AVAudioPlayer(contentsOf: url)
            p.numberOfLoops = -1
            p.volume = 1.0
            p.prepareToPlay()
            p.play()
            player = p
        } catch {
            NSLog("[Ringback] failed to start: \(error)")
        }
    }

    func stop() {
        player?.stop()
        player = nil
    }
}
