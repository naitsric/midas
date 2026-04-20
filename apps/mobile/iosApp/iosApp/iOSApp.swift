import SwiftUI
import AVFoundation
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
                .background(Color.black)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    /// Lee la API key fresca desde el SettingsRepository compartido (KMP) cada vez
    /// que se ejecuta una request. Esto significa que login/logout en la pantalla
    /// Compose se refleja inmediatamente en los services nativos sin reiniciar la app.
    private static func currentApiKey() -> String {
        MidasContext.shared.currentApiKey() ?? ""
    }

    let callControlService = CallControlService(
        config: CallControlConfig(
            apiBaseUrl: URL(string: "https://3vv9l6deii.execute-api.us-east-1.amazonaws.com")!,
            apiKeyProvider: AppDelegate.currentApiKey
        )
    )
    let backendApi = BackendApiService(
        config: BackendApiConfig(
            baseUrl: URL(string: "http://InfraS-Backe-Rw986DMpmSLZ-46441931.us-east-1.elb.amazonaws.com")!,
            apiKeyProvider: AppDelegate.currentApiKey
        )
    )
    lazy var callKitManager = CallKitManager(service: callControlService)
    lazy var pushKitHandler = PushKitHandler()
    lazy var voipDispatcher = SwiftVoipDispatcher(callKitManager: callKitManager)
    lazy var voipCallManager = VoipCallFactoryKt.createVoipCallManager(dispatcher: voipDispatcher)

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Make the VoipCallManager visible to Compose screens via the shared MidasContext.
        MidasContext.shared.voipCallManager = voipCallManager
        pushKitHandler.delegate = self
        pushKitHandler.start()
        // Proactively request microphone access at launch so the user gets the
        // iOS prompt before they tap "Llamar". Without this, Chime fails to start
        // with `audioPermissionError`. AVAudioApplication.requestRecordPermission
        // is iOS 17+; we still support iOS 16 so use the older API.
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            NSLog("[Voip] mic permission granted=\(granted)")
        }
        return true
    }
}

extension AppDelegate: PushKitHandlerDelegate {
    func pushKitDidUpdateToken(_ token: String) {
        NSLog("VoIP push token: \(token)")
        Task {
            do {
                try await backendApi.registerVoipDeviceToken(token)
                NSLog("[VoIP] device token registered with backend")
            } catch {
                NSLog("[VoIP] failed to register token: \(error.localizedDescription)")
            }
        }
    }

    func pushKitDidInvalidateToken() {
        NSLog("VoIP push token invalidated")
    }

    func pushKitDidReceiveIncomingCall(
        payload: IncomingCallPayload,
        completion: @escaping () -> Void
    ) {
        callKitManager.handleIncomingCall(
            callId: payload.callId,
            callerNumber: payload.callerNumber,
            callerName: payload.callerName,
            meetingConfig: payload.meetingConfig
        ) { _ in
            completion()
        }
        voipCallManager.onIncomingCall(
            callId: payload.callId,
            callerNumber: payload.callerNumber,
            callerName: payload.callerName
        )
    }
}

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let vc = MainViewControllerKt.MainViewController()
        vc.view.backgroundColor = .black
        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
