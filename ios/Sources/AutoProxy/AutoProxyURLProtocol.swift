import Foundation

/// Custom URLProtocol that intercepts HTTP/HTTPS requests and routes them through the configured proxy.
class AutoProxyURLProtocol: URLProtocol {
    static let handledKey = "AutoProxyHandled"

    private var internalSession: URLSession?
    private var internalTask: URLSessionDataTask?
    private var sessionDelegate: ProxySessionDelegate?

    // MARK: - URLProtocol overrides

    override class func canInit(with request: URLRequest) -> Bool {
        guard AutoProxy.shared.isEnabled else { return false }
        guard property(forKey: handledKey, in: request) == nil else { return false }
        guard let scheme = request.url?.scheme?.lowercased(),
              scheme == "http" || scheme == "https" else { return false }
        return true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        return request
    }

    override func startLoading() {
        let mutable = (request as NSURLRequest).mutableCopy() as! NSMutableURLRequest
        Self.setProperty(true, forKey: Self.handledKey, in: mutable)

        // Build an ephemeral config with the proxy dictionary but WITHOUT our protocol class
        // to prevent infinite recursion.
        let config = URLSessionConfiguration.ephemeral
        config.connectionProxyDictionary = AutoProxy.shared.proxyDictionary
        config.protocolClasses = []

        let delegate = ProxySessionDelegate(protocolClient: client, urlProtocol: self)
        self.sessionDelegate = delegate

        let session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
        self.internalSession = session

        let task = session.dataTask(with: mutable as URLRequest)
        self.internalTask = task
        task.resume()
    }

    override func stopLoading() {
        internalTask?.cancel()
        internalTask = nil
        internalSession?.invalidateAndCancel()
        internalSession = nil
        sessionDelegate = nil
    }
}
