import Foundation
import WatchConnectivity
import HoldApp

final class PhoneLink: NSObject, ObservableObject, WCSessionDelegate {
    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        WCSession.default.activate()
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        file(message)
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        file(applicationContext)
    }

    func session(_ session: WCSession, didReceive file: WCSessionFile) {
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent("hold-\(UUID().uuidString).m4a")
        try? FileManager.default.copyItem(at: file.fileURL, to: dest)
        DispatchQueue.main.async {
            HoldBridge.shared.audio(path: dest.path)
        }
    }

    private func file(_ message: [String: Any]) {
        let kind = message["kind"] as? String ?? "note"
        let text = message["text"] as? String ?? ""
        guard !text.isEmpty else { return }
        DispatchQueue.main.async {
            HoldBridge.shared.text(kind: kind, text: text)
        }
    }
}
