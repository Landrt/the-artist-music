#!/usr/bin/env bash
set -e
export JAVA_HOME="$HOME/.local/jdk-17"
export PATH="$JAVA_HOME/bin:$HOME/.local/gradle-8.10.2/bin:$PATH"
cd "$(dirname "$0")"
exec gradle :desktopPreview:run --no-daemon
