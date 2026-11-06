#!/bin/sh
# Prefer the caller's SDK/JDK; detect the existing local Pocket Live toolchain otherwise.
TREE_TOOLCHAIN="$HOME/Documents/Codex/2026-10-25/task/.toolchains"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$TREE_TOOLCHAIN/jdk/jdk-21.0.13+9/Contents/Home" ]; then
  export JAVA_HOME="$TREE_TOOLCHAIN/jdk/jdk-21.0.13+9/Contents/Home"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$TREE_TOOLCHAIN/android-sdk" ]; then
  export ANDROID_HOME="$TREE_TOOLCHAIN/android-sdk"
fi
if [ -z "${GRADLE_USER_HOME:-}" ] && [ -d "$TREE_TOOLCHAIN/gradle-home" ]; then
  export GRADLE_USER_HOME="$TREE_TOOLCHAIN/gradle-home"
fi
export PATH="${JAVA_HOME:-/usr}/bin:${ANDROID_HOME:-/opt/android}/platform-tools:$PATH"
