#!/usr/bin/env bash
set -euo pipefail

FLAVOR="${1:-tempus}"
BUILD_TYPE="${2:-debug}"

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

./gradlew "assemble$(tr '[:lower:]' '[:upper:]' <<< "${FLAVOR:0:1}")${FLAVOR:1}$(tr '[:lower:]' '[:upper:]' <<< "${BUILD_TYPE:0:1}")${BUILD_TYPE:1}"

echo ""
find app/build/outputs/apk -name "*.apk" | sort
