import AuthenticationServices
import ComposeApp
import Foundation
import UIKit

final class IOSAppleSignInCredentialProvider: NSObject, AppleSignInCredentialProvider {
    var isConfigured: Bool {
        true
    }

    private var currentCoordinator: AppleAuthorizationCoordinator?

    func requestCredential(completionHandler: @escaping (AppleSignInCredential?, Error?) -> Void) {
        Task { @MainActor in
            let request = ASAuthorizationAppleIDProvider().createRequest()
            let coordinator = AppleAuthorizationCoordinator { [weak self] result in
                self?.currentCoordinator = nil
                switch result {
                case .success(let credential):
                    completionHandler(credential, nil)
                case .failure(let error):
                    completionHandler(nil, error)
                }
            }
            currentCoordinator = coordinator

            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = coordinator
            controller.presentationContextProvider = coordinator
            controller.performRequests()
        }
    }
}

private final class AppleAuthorizationCoordinator: NSObject,
    ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private let completion: (Result<AppleSignInCredential?, Error>) -> Void

    init(completion: @escaping (Result<AppleSignInCredential?, Error>) -> Void) {
        self.completion = completion
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let identityToken = credential.identityToken,
            let idToken = String(data: identityToken, encoding: .utf8),
            !idToken.isEmpty
        else {
            completion(.success(nil))
            return
        }

        let authorizationCode = credential.authorizationCode
            .flatMap { String(data: $0, encoding: .utf8) }
            .flatMap { $0.isEmpty ? nil : $0 }

        completion(
            .success(
                AppleSignInCredential(
                    idToken: idToken,
                    authorizationCode: authorizationCode
                )
            )
        )
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        if let authorizationError = error as? ASAuthorizationError,
           authorizationError.code == .canceled {
            completion(.success(nil))
            return
        }

        completion(.failure(error))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.keyWindow ?? ASPresentationAnchor()
    }
}

private extension UIApplication {
    var keyWindow: UIWindow? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
    }
}
