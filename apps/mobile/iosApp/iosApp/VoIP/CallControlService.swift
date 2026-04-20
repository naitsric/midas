import Foundation

struct CallControlConfig {
    let apiBaseUrl: URL
    let apiKeyProvider: () -> String
}

struct DialResponse: Decodable {
    let callId: String
    let meetingId: String
    let joinToken: String
    let attendeeId: String
    let externalUserId: String
    let mediaRegion: String
    let audioHostUrl: String
    let audioFallbackUrl: String
    let signalingUrl: String
    let turnControlUrl: String
}

enum CallControlError: Error {
    case invalidResponse
    case httpError(status: Int, body: String?)
    case networkError(Error)
}

final class CallControlService {
    private let config: CallControlConfig
    private let session: URLSession

    init(config: CallControlConfig, session: URLSession = .shared) {
        self.config = config
        self.session = session
    }

    func dial(toNumber: String, clientName: String?) async throws -> DialResponse {
        let body: [String: Any] = [
            "toNumber": toNumber,
            "clientName": clientName ?? NSNull(),
        ]
        return try await post(path: "/calls/dial", body: body)
    }

    func answer(callId: String) async throws {
        let _: EmptyResponse = try await post(path: "/calls/answer", body: ["callId": callId])
    }

    func hangup(callId: String) async throws {
        let _: EmptyResponse = try await post(path: "/calls/hangup", body: ["callId": callId])
    }

    struct StatusResponse: Decodable {
        let callId: String
        let status: String
        let meetingId: String?
    }

    func getStatus(callId: String) async throws -> StatusResponse {
        let url = config.apiBaseUrl
            .appendingPathComponent("/calls/status")
            .appending(queryItems: [URLQueryItem(name: "callId", value: callId)])
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue(config.apiKeyProvider(), forHTTPHeaderField: "X-API-Key")
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw CallControlError.invalidResponse
        }
        return try JSONDecoder().decode(StatusResponse.self, from: data)
    }

    private struct EmptyResponse: Decodable {}

    private func post<T: Decodable>(path: String, body: [String: Any]) async throws -> T {
        let url = config.apiBaseUrl.appendingPathComponent(path)
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(config.apiKeyProvider(), forHTTPHeaderField: "X-API-Key")
        req.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])

        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else {
                throw CallControlError.invalidResponse
            }
            guard (200..<300).contains(http.statusCode) else {
                let bodyStr = String(data: data, encoding: .utf8)
                throw CallControlError.httpError(status: http.statusCode, body: bodyStr)
            }
            if T.self == EmptyResponse.self {
                return EmptyResponse() as! T
            }
            return try JSONDecoder().decode(T.self, from: data)
        } catch let error as CallControlError {
            throw error
        } catch {
            throw CallControlError.networkError(error)
        }
    }
}
