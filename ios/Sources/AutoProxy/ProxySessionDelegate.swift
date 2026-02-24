import Foundation
import Security

/// URLSession delegate that handles SSL trust for the proxy CA and forwards responses back to the URLProtocolClient.
class ProxySessionDelegate: NSObject, URLSessionDataDelegate {
    weak var protocolClient: URLProtocolClient?
    weak var urlProtocol: URLProtocol?

    init(protocolClient: URLProtocolClient?, urlProtocol: URLProtocol) {
        self.protocolClient = protocolClient
        self.urlProtocol = urlProtocol
        super.init()
    }

    // MARK: - SSL Trust

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // Add the proxy CA as a trusted anchor alongside system CAs
        if let proxyCert = AutoProxy.shared.proxyCertificate {
            SecTrustSetAnchorCertificates(serverTrust, [proxyCert] as CFArray)
            SecTrustSetAnchorCertificatesOnly(serverTrust, false)
        }

        var error: CFError?
        if SecTrustEvaluateWithError(serverTrust, &error) {
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        } else {
            NSLog("[AutoProxy] SSL trust evaluation failed: %@",
                  error.map { String(describing: $0) } ?? "unknown")
            completionHandler(.performDefaultHandling, nil)
        }
    }

    // MARK: - Response forwarding

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        guard let proto = urlProtocol else { return }
        protocolClient?.urlProtocol(proto, didReceive: response, cacheStoragePolicy: .notAllowed)
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard let proto = urlProtocol else { return }
        protocolClient?.urlProtocol(proto, didLoad: data)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let proto = urlProtocol else { return }
        if let error = error {
            protocolClient?.urlProtocol(proto, didFailWithError: error)
        } else {
            protocolClient?.urlProtocolDidFinishLoading(proto)
        }
    }

    // MARK: - Redirect handling

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard let proto = urlProtocol else {
            completionHandler(nil)
            return
        }
        protocolClient?.urlProtocol(proto, wasRedirectedTo: request, redirectResponse: response)
        completionHandler(request)
    }
}
