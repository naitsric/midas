import Foundation

struct BackendApiConfig {
    let baseUrl: URL
    let apiKeyProvider: () -> String
}

final class BackendApiService {
    private let config: BackendApiConfig
    private let session: URLSession

    init(config: BackendApiConfig, session: URLSession = .shared) {
        self.config = config
        self.session = session
    }

    func registerVoipDeviceToken(_ token: String) async throws {
        let apiKey = config.apiKeyProvider()
        guard !apiKey.isEmpty else {
            NSLog("[BackendApi] no API key configured — skipping VoIP token register")
            return
        }
        let url = config.baseUrl.appendingPathComponent("/api/advisors/me/voip-token")
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(apiKey, forHTTPHeaderField: "X-API-Key")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["device_token": token])

        let (data, resp) = try await session.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw NSError(
                domain: "BackendApi", code: 0,
                userInfo: [NSLocalizedDescriptionKey: "VoIP token register failed: \(body)"]
            )
        }
    }
}
