#!/usr/bin/env bash
set -euo pipefail

FLAVOR="${1:-tempus}"
BUILD_TYPE="${2:-debug}"

# Respect a working JAVA_HOME; otherwise pick a JDK for this OS (script used to hardcode Linux paths).
if [[ -z "${JAVA_HOME:-}" || ! -d "${JAVA_HOME}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  case "$(uname -s)" in
    Darwin)
      JAVA_HOME="$(
        /usr/libexec/java_home -v 17 2>/dev/null \
          || /usr/libexec/java_home -v 21 2>/dev/null \
          || /usr/libexec/java_home 2>/dev/null
      )"
      ;;
    Linux)
      JAVA_HOME=""
      for candidate in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/java-17; do
        if [[ -d "${candidate}" && -x "${candidate}/bin/java" ]]; then
          JAVA_HOME="${candidate}"
          break
        fi
      done
      ;;
  esac
  export JAVA_HOME
fi

if [[ -z "${JAVA_HOME:-}" || ! -d "${JAVA_HOME}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "ERROR: No usable JDK found. Install JDK 17+ (macOS: brew install openjdk@17) or set JAVA_HOME." >&2
  exit 1
fi

./gradlew "assemble$(tr '[:lower:]' '[:upper:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}$(tr '[:lower:]' '[:upper:]' <<< "${BUILD_TYPE:0:1}")${BUILD_TYPE:1}"

echo ""
find app/build/outputs/apk -name "*.apk" | sort
