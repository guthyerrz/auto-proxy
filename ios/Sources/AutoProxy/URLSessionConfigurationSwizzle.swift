import Foundation

/// Swizzles URLSessionConfiguration.default and .ephemeral to inject AutoProxyURLProtocol
/// into every new session's protocolClasses.
enum URLSessionConfigurationSwizzle {
    private static var installed = false

    static func install() {
        guard !installed else { return }
        installed = true

        swizzleConfigurationProperty("defaultSessionConfiguration")
        swizzleConfigurationProperty("ephemeralSessionConfiguration")
    }

    private static func swizzleConfigurationProperty(_ selectorName: String) {
        let cls: AnyClass = URLSessionConfiguration.self

        let originalSelector = Selector(selectorName)
        let swizzledSelector = Selector("autoProxy_\(selectorName)")

        guard let originalMethod = class_getClassMethod(cls, originalSelector),
              let swizzledMethod = class_getClassMethod(cls, swizzledSelector) else {
            NSLog("[AutoProxy] Failed to swizzle %@", selectorName)
            return
        }

        method_exchangeImplementations(originalMethod, swizzledMethod)
    }
}

extension URLSessionConfiguration {
    @objc dynamic class func autoProxy_defaultSessionConfiguration() -> URLSessionConfiguration {
        // After swizzling, this actually calls the original implementation
        let config = autoProxy_defaultSessionConfiguration()
        config.injectAutoProxyProtocol()
        return config
    }

    @objc dynamic class func autoProxy_ephemeralSessionConfiguration() -> URLSessionConfiguration {
        let config = autoProxy_ephemeralSessionConfiguration()
        config.injectAutoProxyProtocol()
        return config
    }

    private func injectAutoProxyProtocol() {
        guard AutoProxy.shared.isEnabled else { return }

        var classes = protocolClasses ?? []
        if !classes.contains(where: { $0 == AutoProxyURLProtocol.self }) {
            classes.insert(AutoProxyURLProtocol.self, at: 0)
        }
        protocolClasses = classes
    }
}
