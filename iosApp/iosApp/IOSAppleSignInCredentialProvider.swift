import AuthenticationServices
import ComposeApp
import Foundation
import UIKit

final class IOSAppleSignInCredentialProvider: NSObject, AppleSignInCredentialProvider {
    var isConfigured: Bool {
        true
    }

    private var currentCoordinator: AppleAuthorizationCoordinator?

    func requestCredential(completionHandler: @escaping (AppleSignInCredentialResult?, Error?) -> Void) {
        Task { @MainActor in
            let request = ASAuthorizationAppleIDProvider().createRequest()
            let coordinator = AppleAuthorizationCoordinator { [weak self] result in
                self?.currentCoordinator = nil
                switch result {
                case .success(let outcome):
                    switch outcome {
                    case .credential(let credential):
                        completionHandler(
                            AppleSignInCredentialResult(credential: credential, isCanceled: false),
                            nil
                        )
                    case .canceled:
                        completionHandler(
                            AppleSignInCredentialResult(credential: nil, isCanceled: true),
                            nil
                        )
                    case .unavailable:
                        completionHandler(
                            AppleSignInCredentialResult(credential: nil, isCanceled: false),
                            nil
                        )
                    }
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
    private let completion: (Result<AppleAuthorizationOutcome, Error>) -> Void

    init(completion: @escaping (Result<AppleAuthorizationOutcome, Error>) -> Void) {
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
            completion(.success(.unavailable))
            return
        }

        let authorizationCode = credential.authorizationCode
            .flatMap { String(data: $0, encoding: .utf8) }
            .flatMap { $0.isEmpty ? nil : $0 }

        completion(
            .success(
                .credential(
                    AppleSignInCredential(
                        idToken: idToken,
                        authorizationCode: authorizationCode
                    )
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
            completion(.success(.canceled))
            return
        }

        completion(.failure(error))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.keyWindow ?? ASPresentationAnchor()
    }
}

private enum AppleAuthorizationOutcome {
    case credential(AppleSignInCredential)
    case canceled
    case unavailable
}

private extension UIApplication {
    var keyWindow: UIWindow? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
    }
}
