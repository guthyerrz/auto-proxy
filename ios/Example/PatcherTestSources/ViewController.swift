import UIKit

/// Minimal test app for the IPA patcher. Does NOT include AutoProxy SDK.
/// After patching, AutoProxy should be injected and proxy traffic through the configured proxy.
class ViewController: UIViewController {
    private let statusLabel = UILabel()
    private let responseLabel = UILabel()
    private let activityIndicator = UIActivityIndicatorView(style: .medium)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Patcher Test App"
        view.backgroundColor = .systemBackground
        setupUI()

        // Auto-fire a request on launch so we can verify proxy interception
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.testHTTPS()
        }
    }

    private func setupUI() {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 16
        stack.alignment = .fill
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
        ])

        statusLabel.numberOfLines = 0
        statusLabel.font = .monospacedSystemFont(ofSize: 14, weight: .regular)
        statusLabel.text = "This app has NO AutoProxy SDK.\nIf proxy interception works after patching,\nthe patcher is working correctly."
        stack.addArrangedSubview(statusLabel)

        let separator = UIView()
        separator.backgroundColor = .separator
        separator.heightAnchor.constraint(equalToConstant: 1).isActive = true
        stack.addArrangedSubview(separator)

        let header = UILabel()
        header.text = "Test Requests"
        header.font = .preferredFont(forTextStyle: .headline)
        stack.addArrangedSubview(header)

        let httpsButton = UIButton(type: .system)
        httpsButton.setTitle("GET https://httpbin.org/get", for: .normal)
        httpsButton.addTarget(self, action: #selector(testHTTPS), for: .touchUpInside)
        httpsButton.contentHorizontalAlignment = .leading
        stack.addArrangedSubview(httpsButton)

        let ipButton = UIButton(type: .system)
        ipButton.setTitle("GET https://httpbin.org/ip", for: .normal)
        ipButton.addTarget(self, action: #selector(testIP), for: .touchUpInside)
        ipButton.contentHorizontalAlignment = .leading
        stack.addArrangedSubview(ipButton)

        activityIndicator.hidesWhenStopped = true
        stack.addArrangedSubview(activityIndicator)

        responseLabel.numberOfLines = 0
        responseLabel.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
        responseLabel.textColor = .secondaryLabel
        stack.addArrangedSubview(responseLabel)
    }

    @objc private func testHTTPS() {
        performRequest(URL(string: "https://httpbin.org/get")!)
    }

    @objc private func testIP() {
        performRequest(URL(string: "https://httpbin.org/ip")!)
    }

    private func performRequest(_ url: URL) {
        responseLabel.text = nil
        activityIndicator.startAnimating()

        NSLog("[PatcherTestApp] Making request to %@", url.absoluteString)

        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            DispatchQueue.main.async {
                self?.activityIndicator.stopAnimating()

                if let error = error {
                    NSLog("[PatcherTestApp] Error: %@", error.localizedDescription)
                    self?.responseLabel.text = "Error: \(error.localizedDescription)"
                    return
                }

                guard let http = response as? HTTPURLResponse else {
                    self?.responseLabel.text = "Error: No HTTP response"
                    return
                }

                NSLog("[PatcherTestApp] Response: %d", http.statusCode)
                var text = "Status: \(http.statusCode)\n"
                if let data = data, let body = String(data: data, encoding: .utf8) {
                    let truncated = String(body.prefix(500))
                    text += "\n\(truncated)"
                }
                self?.responseLabel.text = text
            }
        }.resume()
    }
}
