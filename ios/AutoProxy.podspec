Pod::Spec.new do |s|
  s.name         = 'AutoProxy'
  s.version      = '0.1.0'
  s.summary      = 'Zero-code HTTP/HTTPS proxy injection for iOS apps'
  s.description  = <<-DESC
    AutoProxy intercepts network traffic without requiring app code changes.
    Proxy settings are read from launch arguments or UserDefaults, making it
    ideal for automation and traffic inspection with tools like mitmproxy.
  DESC
  s.homepage     = 'https://github.com/guthyerrz/auto-proxy'
  s.license      = { :type => 'MIT', :file => '../LICENSE' }
  s.author       = { 'guthyerrz' => 'guthyerrz@users.noreply.github.com' }
  s.source       = { :git => 'https://github.com/guthyerrz/auto-proxy.git', :tag => s.version.to_s }

  s.ios.deployment_target = '15.0'
  s.swift_version = '5.9'

  s.source_files = 'Sources/AutoProxy/**/*.{swift,h,m}'
  s.resource_bundles = { 'AutoProxy' => ['Sources/AutoProxy/Resources/**/*'] }

  s.frameworks = 'Foundation', 'Security'
end
