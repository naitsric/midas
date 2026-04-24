import EventKit
import Foundation
import ComposeApp

/// Implementación nativa del CalendarBridge declarado en KMP commonMain.
/// Crea eventos en el calendar default del usuario con un alarm 5 min antes.
final class CalendarBridgeImpl: NSObject, CalendarBridge {
    private let store = EKEventStore()
    private let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let alarmOffsetSeconds: TimeInterval = -300  // 5 min antes

    func createEvent(
        title: String,
        whenIso: String,
        durationMinutes: Int32,
        onSuccess: @escaping (String) -> Void,
        onError: @escaping (String, String) -> Void
    ) {
        Task {
            // 1) Permission
            let granted: Bool
            do {
                if #available(iOS 17.0, *) {
                    granted = try await store.requestFullAccessToEvents()
                } else {
                    granted = await withCheckedContinuation { cont in
                        store.requestAccess(to: .event) { ok, _ in cont.resume(returning: ok) }
                    }
                }
            } catch {
                onError("store_error", error.localizedDescription)
                return
            }
            guard granted else {
                onError("permission_denied", "Sin permiso de Calendar")
                return
            }

            // 2) Parse ISO 8601 — tolera tanto "...Z" como "...-05:00", con o sin
            //    fracciones de segundo.
            guard let startDate = parseIso(whenIso) else {
                onError("invalid_date", "Fecha invalida: \(whenIso)")
                return
            }

            // 3) Build + save event
            let event = EKEvent(eventStore: store)
            event.title = title
            event.startDate = startDate
            event.endDate = startDate.addingTimeInterval(TimeInterval(durationMinutes) * 60)
            event.calendar = store.defaultCalendarForNewEvents
            event.addAlarm(EKAlarm(relativeOffset: Self.alarmOffsetSeconds))

            do {
                try store.save(event, span: .thisEvent)
                let id = event.eventIdentifier ?? ""
                onSuccess(id)
            } catch {
                onError("store_error", error.localizedDescription)
            }
        }
    }

    private func parseIso(_ raw: String) -> Date? {
        // Try with fractional seconds first, then without
        if let d = isoFormatter.date(from: raw) { return d }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        return plain.date(from: raw)
    }
}
