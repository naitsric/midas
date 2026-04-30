import AVFoundation
import Foundation
import ComposeApp

/// AVPlayer wrapper que cumple el `AudioPlayerBridge` declarado en KMP.
///
/// Comportamiento:
/// - `load`: crea AVPlayer, espera a que el item esté .readyToPlay, callback
///   con duración. Mientras está playing emite progress ~10Hz vía
///   `addPeriodicTimeObserver`. Detecta fin via NotificationCenter.
/// - `play` / `pause`: control directo del AVPlayer.
/// - `seek(seconds)`: salto preciso (toleranceBefore/after = .zero).
/// - `release`: cancela observers y suelta referencia al player.
///
/// AVPlayer maneja URLs HTTPS pre-firmadas de S3 sin configuración extra
/// (sin necesidad de App Transport Security overrides — son HTTPS válidas).
final class AudioPlayerBridgeImpl: NSObject, AudioPlayerBridge {

    private var player: AVPlayer?
    private var item: AVPlayerItem?
    private var statusObserver: NSKeyValueObservation?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?

    // KotlinDouble (NSNumber-like wrapper) — KMP exports lambda primitives boxed
    // because Obj-C blocks can't carry naked Double values across the bridge.
    private var onReady: ((KotlinDouble) -> Void)?
    private var onProgress: ((KotlinDouble) -> Void)?
    private var onCompleted: (() -> Void)?
    private var onError: ((String) -> Void)?

    func load(
        url: String,
        onReady: @escaping (KotlinDouble) -> Void,
        onProgress: @escaping (KotlinDouble) -> Void,
        onCompleted: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        // Reset previous session, if any
        release()

        guard let parsed = URL(string: url) else {
            onError("URL inválida")
            return
        }

        self.onReady = onReady
        self.onProgress = onProgress
        self.onCompleted = onCompleted
        self.onError = onError

        // Configure session for playback through the speaker (mixWithOthers
        // lets the user keep music/podcasts paused naturally).
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
        } catch {
            // Non-fatal; AVPlayer will still work in most cases.
            NSLog("[AudioPlayer] AVAudioSession setup failed: \(error.localizedDescription)")
        }

        let newItem = AVPlayerItem(url: parsed)
        let newPlayer = AVPlayer(playerItem: newItem)
        self.item = newItem
        self.player = newPlayer

        statusObserver = newItem.observe(\.status, options: [.new]) { [weak self] item, _ in
            guard let self else { return }
            switch item.status {
            case .readyToPlay:
                let duration = CMTimeGetSeconds(item.asset.duration)
                let safe = duration.isFinite && duration > 0 ? duration : 0
                self.onReady?(KotlinDouble(value: safe))
                self.installTimeObserver()
            case .failed:
                let msg = item.error?.localizedDescription ?? "Error desconocido"
                self.onError?(msg)
            default:
                break
            }
        }

        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: newItem,
            queue: .main
        ) { [weak self] _ in
            self?.onCompleted?()
        }
    }

    func play() {
        player?.play()
    }

    func pause() {
        player?.pause()
    }

    func seek(positionSeconds: Double) {
        guard let player else { return }
        let target = CMTime(seconds: positionSeconds, preferredTimescale: 600)
        player.seek(to: target, toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func release() {
        if let token = timeObserver {
            player?.removeTimeObserver(token)
        }
        timeObserver = nil
        statusObserver?.invalidate()
        statusObserver = nil
        if let obs = endObserver {
            NotificationCenter.default.removeObserver(obs)
        }
        endObserver = nil
        player?.pause()
        player = nil
        item = nil
        onReady = nil
        onProgress = nil
        onCompleted = nil
        onError = nil
    }

    private func installTimeObserver() {
        guard let player else { return }
        let interval = CMTime(seconds: 0.1, preferredTimescale: 600)
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: interval,
            queue: .main
        ) { [weak self] time in
            self?.onProgress?(KotlinDouble(value: CMTimeGetSeconds(time)))
        }
    }
}
