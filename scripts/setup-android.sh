#!/usr/bin/env bash
# 一次性准备 macOS + Apple Silicon 上的 Android 开发环境。
# 幂等：已安装的步骤会跳过。
set -euo pipefail

JDK_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
CMDLINE_TOOLS="/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin"

export HOMEBREW_NO_AUTO_UPDATE=1

echo "==> JDK 21"
if [ ! -d "$JDK_HOME" ]; then
  brew install openjdk@21
else
  echo "已安装：$JDK_HOME"
fi
export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> Android command-line tools"
if [ ! -d "$CMDLINE_TOOLS" ]; then
  brew install --cask android-commandlinetools
else
  echo "已安装：$CMDLINE_TOOLS"
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$CMDLINE_TOOLS:$SDK/platform-tools:$SDK/emulator:$PATH"

echo "==> SDK 许可"
# `yes` 会被 SIGPIPE 杀死，与 pipefail 叠加会误判失败，所以这里吞掉退出码
yes | sdkmanager --sdk_root="$SDK" --licenses >/dev/null || true

echo "==> SDK 组件"
sdkmanager --sdk_root="$SDK" \
  "platform-tools" \
  "platforms;android-37.1" \
  "build-tools;37.0.0" \
  "emulator" \
  "system-images;android-35;google_apis;arm64-v8a"

echo "==> AVD"
if ! emulator -list-avds 2>/dev/null | grep -qx gtd35; then
  echo no | avdmanager create avd -n gtd35 \
    -k "system-images;android-35;google_apis;arm64-v8a" \
    --device "pixel_7"
else
  echo "已存在：gtd35"
fi

echo
echo "完成。把下面几行加进 shell 配置："
echo "  export JAVA_HOME=\"$JDK_HOME\""
echo "  export ANDROID_HOME=\"$SDK\""
echo "  export PATH=\"$CMDLINE_TOOLS:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator:\$PATH\""
