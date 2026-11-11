import SwiftUI
import HoldApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct HOLDApp: App {
    @StateObject private var link = PhoneLink()

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .onAppear { link.activate() }
        }
    }
}
