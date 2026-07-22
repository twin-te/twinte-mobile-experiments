import Foundation
import UIKit
import ComposeApp
import GoogleSignIn

final class IOSGoogleIdTokenProvider: NSObject, GoogleIdTokenProvider {
    var isConfigured: Bool {
        GoogleSignInConfig.current != nil
    }

    func requestIdToken(completionHandler: @escaping (GoogleIdTokenResult?, Error?) -> Void) {
        Task { @MainActor in
            guard let config = GoogleSignInConfig.current else {
                completionHandler(GoogleIdTokenResult(idToken: nil, isCanceled: false), nil)
                return
            }
            guard let presentingViewController = UIApplication.shared.topViewController else {
                completionHandler(GoogleIdTokenResult(idToken: nil, isCanceled: false), nil)
                return
            }

            GIDSignIn.sharedInstance.configuration = config

            do {
                let result = try await GIDSignIn.sharedInstance.signIn(
                    withPresenting: presentingViewController
                )
                let user = try await result.user.refreshTokensIfNeeded()
                completionHandler(
                    GoogleIdTokenResult(
                        idToken: user.idToken?.tokenString,
                        isCanceled: false
                    ),
                    nil
                )
            } catch {
                if (error as NSError).code == GIDSignInError.canceled.rawValue {
                    completionHandler(GoogleIdTokenResult(idToken: nil, isCanceled: true), nil)
                    return
                }
                completionHandler(nil, error)
            }
        }
    }
}

private enum GoogleSignInConfig {
    static var current: GIDConfiguration? {
        guard
            let clientId = Bundle.main.nonEmptyInfoString(forKey: "GIDClientID"),
            let serverClientId = Bundle.main.nonEmptyInfoString(forKey: "GIDServerClientID")
        else {
            return nil
        }
        return GIDConfiguration(clientID: clientId, serverClientID: serverClientId)
    }
}

extension Bundle {
    func nonEmptyInfoString(forKey key: String) -> String? {
        guard let value = object(forInfoDictionaryKey: key) as? String else {
            return nil
        }
        let trimmedValue = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedValue.isEmpty, !trimmedValue.hasPrefix("$(") else {
            return nil
        }
        return trimmedValue
    }
}

private extension UIApplication {
    var topViewController: UIViewController? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController?
            .topPresentedViewController
    }
}

private extension UIViewController {
    var topPresentedViewController: UIViewController {
        if let presentedViewController {
            return presentedViewController.topPresentedViewController
        }
        if let navigationController = self as? UINavigationController,
           let visibleViewController = navigationController.visibleViewController {
            return visibleViewController.topPresentedViewController
        }
        if let tabBarController = self as? UITabBarController,
           let selectedViewController = tabBarController.selectedViewController {
            return selectedViewController.topPresentedViewController
        }
        return self
    }
}
