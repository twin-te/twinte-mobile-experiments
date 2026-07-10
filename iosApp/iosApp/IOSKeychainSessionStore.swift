import Foundation
import Security
import ComposeApp

final class IOSKeychainSessionStore: NSObject, SessionStore {
    func getSessionId(completionHandler: @escaping (String?, Error?) -> Void) {
        do {
            completionHandler(try readSessionId(), nil)
        } catch {
            completionHandler(nil, error)
        }
    }

    func saveSessionId(sessionId: String, completionHandler: @escaping (Error?) -> Void) {
        do {
            try saveSessionId(sessionId)
            completionHandler(nil)
        } catch {
            completionHandler(error)
        }
    }

    func clearSessionId(completionHandler: @escaping (Error?) -> Void) {
        do {
            try deleteSessionId()
            completionHandler(nil)
        } catch {
            completionHandler(error)
        }
    }

    private func readSessionId() throws -> String? {
        var query = keychainQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess else {
            throw KeychainError.unexpectedStatus(status)
        }
        guard let data = result as? Data else {
            throw KeychainError.invalidData
        }
        return String(data: data, encoding: .utf8)
    }

    private func saveSessionId(_ sessionId: String) throws {
        let attributes = [
            kSecValueData as String: Data(sessionId.utf8),
        ]
        let updateStatus = SecItemUpdate(keychainQuery() as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecSuccess {
            return
        }
        guard updateStatus == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(updateStatus)
        }

        var addQuery = keychainQuery()
        addQuery[kSecValueData as String] = Data(sessionId.utf8)
        let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw KeychainError.unexpectedStatus(addStatus)
        }
    }

    private func deleteSessionId() throws {
        let status = SecItemDelete(keychainQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(status)
        }
    }

    private func keychainQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
    }

    private let service = "net.twinte.mobile_experiments.auth"
    private let account = "session_id"
}

private enum KeychainError: Error {
    case invalidData
    case unexpectedStatus(OSStatus)
}
