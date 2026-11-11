import SwiftUI
import AVFoundation
import WatchConnectivity

@main
struct HOLDWatchApp: App {
    @StateObject private var link = WatchLink()

    var body: some Scene {
        WindowGroup {
            WatchHome(link: link)
        }
    }
}

struct WatchHome: View {
    @ObservedObject var link: WatchLink
    @State private var title = ""
    @State private var bodyText = ""
    @State private var recording = false
    @State private var recorder: AVAudioRecorder?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                TextField("Title", text: $title)
                    .font(.headline)
                TextField("Write", text: $bodyText, axis: .vertical)
                    .font(.body)
                Button("Save to iPhone") {
                    let text = bodyText.isEmpty ? title : "\(title)\n\(bodyText)"
                    link.send(kind: "note", text: text)
                    title = ""
                    bodyText = ""
                }
                Button(recording ? "Stop" : "Record") { toggle() }
                Text(link.status)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding()
        }
    }

    private func toggle() {
        if recording {
            recorder?.stop()
            recording = false
            if let url = recorder?.url {
                link.sendAudio(url)
            }
            recorder = nil
            return
        }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.record)
        try? session.setActive(true)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("hold-\(UUID().uuidString).m4a")
        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVSampleRateKey: 12_000,
            AVNumberOfChannelsKey: 1,
        ]
        guard let next = try? AVAudioRecorder(url: url, settings: settings) else {
            link.status = "Couldn't open the microphone."
            return
        }
        next.record()
        recorder = next
        recording = true
        link.status = "Recording"
    }
}

final class WatchLink: NSObject, ObservableObject, WCSessionDelegate {
    @Published var status = "Hold"

    override init() {
        super.init()
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func send(kind: String, text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let payload = ["kind": kind, "text": trimmed]
        if WCSession.default.isReachable {
            WCSession.default.sendMessage(payload, replyHandler: nil)
        } else {
            try? WCSession.default.updateApplicationContext(payload)
        }
        status = "Sent to the phone"
    }

    func sendAudio(_ url: URL) {
        WCSession.default.transferFile(url, metadata: ["kind": "voice"])
        status = "Sending the recording"
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}
}
