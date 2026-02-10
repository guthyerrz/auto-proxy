#!/usr/bin/env bash
set -euo pipefail

REPO="guthyerrz/auto-proxy"
INSTALL_DIR="${HOME}/.local/bin"
JAR_NAME="auto-proxy-patcher.jar"
BIN_NAME="auto-proxy"

# --- Helpers ---
info()  { printf '\033[1;34m%s\033[0m\n' "$*"; }
error() { printf '\033[1;31mError: %s\033[0m\n' "$*" >&2; exit 1; }

# --- OS check ---
OS="$(uname -s)"
case "$OS" in
  Darwin|Linux) ;;
  *) error "Unsupported OS: $OS. Only macOS and Linux are supported." ;;
esac

# --- Java check ---
if ! command -v java &>/dev/null; then
  error "Java not found. Install Java 11+ and try again."
fi
JAVA_VERSION=$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/' | cut -d. -f1)
if [ "$JAVA_VERSION" -lt 11 ] 2>/dev/null; then
  error "Java 11+ required (found Java $JAVA_VERSION)."
fi

# --- Version ---
VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  info "Fetching latest version..."
  VERSION=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" | grep '"tag_name"' | sed 's/.*"v\(.*\)".*/\1/')
  [ -z "$VERSION" ] && error "Could not determine latest version."
fi
info "Installing auto-proxy v${VERSION}..."

# --- Download ---
BASE_URL="https://github.com/${REPO}/releases/download/v${VERSION}"
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

curl -fsSL "${BASE_URL}/${JAR_NAME}" -o "${TMPDIR}/${JAR_NAME}"
curl -fsSL "${BASE_URL}/${JAR_NAME}.sha256" -o "${TMPDIR}/${JAR_NAME}.sha256"

# --- Verify checksum ---
cd "$TMPDIR"
if command -v sha256sum &>/dev/null; then
  sha256sum -c "${JAR_NAME}.sha256" || error "Checksum verification failed."
else
  EXPECTED=$(awk '{print $1}' "${JAR_NAME}.sha256")
  ACTUAL=$(shasum -a 256 "${JAR_NAME}" | awk '{print $1}')
  [ "$EXPECTED" = "$ACTUAL" ] || error "Checksum verification failed."
fi
cd - >/dev/null

# --- Install ---
mkdir -p "$INSTALL_DIR"
mv "${TMPDIR}/${JAR_NAME}" "${INSTALL_DIR}/${JAR_NAME}"

cat > "${INSTALL_DIR}/${BIN_NAME}" << 'WRAPPER'
#!/usr/bin/env bash
exec java -jar "$(dirname "$0")/auto-proxy-patcher.jar" "$@"
WRAPPER
chmod +x "${INSTALL_DIR}/${BIN_NAME}"

info "Installed auto-proxy v${VERSION} to ${INSTALL_DIR}/${BIN_NAME}"

# --- PATH hint ---
case ":$PATH:" in
  *":${INSTALL_DIR}:"*) ;;
  *) printf '\n\033[1;33m%s\033[0m\n' "Add ~/.local/bin to your PATH: export PATH=\"\$HOME/.local/bin:\$PATH\"" ;;
esac
