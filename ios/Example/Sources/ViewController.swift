import UIKit
import AutoProxy

class ViewController: UIViewController {
    private let statusLabel = UILabel()
    private let responseLabel = UILabel()
    private let activityIndicator = UIActivityIndicatorView(style: .medium)

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "AutoProxy Example"
        view.backgroundColor = .systemBackground
        setupUI()
        updateStatus()
        NotificationCenter.default.addObserver(
            self, selector: #selector(updateStatus),
            name: AutoProxy.configDidChangeNotification, object: nil)
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

        // Status section
        let statusHeader = makeHeader("Proxy Status")
        stack.addArrangedSubview(statusHeader)

        statusLabel.numberOfLines = 0
        statusLabel.font = .monospacedSystemFont(ofSize: 14, weight: .regular)
        stack.addArrangedSubview(statusLabel)

        // Separator
        let separator = UIView()
        separator.backgroundColor = .separator
        separator.heightAnchor.constraint(equalToConstant: 1).isActive = true
        stack.addArrangedSubview(separator)

        // Test section
        let testHeader = makeHeader("Test Requests")
        stack.addArrangedSubview(testHeader)

        let httpButton = makeButton("GET httpbin.org/get (HTTPS)", action: #selector(testHTTPS))
        stack.addArrangedSubview(httpButton)

        let ipButton = makeButton("GET httpbin.org/ip (HTTPS)", action: #selector(testIP))
        stack.addArrangedSubview(ipButton)

        activityIndicator.hidesWhenStopped = true
        stack.addArrangedSubview(activityIndicator)

        // Response section
        responseLabel.numberOfLines = 0
        responseLabel.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
        responseLabel.textColor = .secondaryLabel
        stack.addArrangedSubview(responseLabel)
    }

    private func makeHeader(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .preferredFont(forTextStyle: .headline)
        return label
    }

    private func makeButton(_ title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.addTarget(self, action: action, for: .touchUpInside)
        button.contentHorizontalAlignment = .leading
        return button
    }

    @objc private func updateStatus() {
        let proxy = AutoProxy.shared
        var lines = [String]()

        lines.append("Enabled:  \(proxy.isEnabled ? "YES" : "NO")")
        if let host = proxy.proxyHost {
            lines.append("Host:     \(host)")
            lines.append("Port:     \(proxy.proxyPort)")
        }
        lines.append("Cert:     \(proxy.proxyCertificate != nil ? "loaded" : "none")")

        statusLabel.text = lines.joined(separator: "\n")
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

        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            DispatchQueue.main.async {
                self?.activityIndicator.stopAnimating()

                if let error = error {
                    self?.responseLabel.text = "Error: \(error.localizedDescription)"
                    return
                }

                guard let http = response as? HTTPURLResponse else {
                    self?.responseLabel.text = "Error: No HTTP response"
                    return
                }

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
