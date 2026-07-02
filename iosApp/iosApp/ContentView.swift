import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            googleIdTokenProvider: IOSGoogleIdTokenProvider(),
            appleSignInCredentialProvider: IOSAppleSignInCredentialProvider(),
            sessionStore: IOSKeychainSessionStore(),
            appBaseUrl: Bundle.main.nonEmptyInfoString(forKey: "TwinteAppBaseURL") ?? "https://app.twinte.net"
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
