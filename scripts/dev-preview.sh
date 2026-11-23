#!/usr/bin/env bash
# 在电脑上预览 orgtd：启动带窗口的模拟器、装最新 debug 包、推一份示例 org 文件。
#
#   ./scripts/dev-preview.sh
#
# 第一次需要在模拟器里手动授权一次目录（系统的安全限制，脚本代替不了）：
#   点「选择目录」→ Downloads → orgtd → 使用此文件夹 → 允许
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
AVD="${AVD:-gtd35}"
SAMPLE="${SAMPLE:-$HOME/org/orglist/gtd.org}"

cd "$ROOT"
echo "==> 构建"
./gradlew :app:assembleDebug --console=plain -q

if ! "$ADB" devices | awk 'NR>1 && $2=="device" {found=1} END {exit !found}'; then
  echo "==> 启动模拟器窗口（$AVD）"
  "$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-snapshot -no-boot-anim >/dev/null 2>&1 &
  "$ADB" wait-for-device
  for _ in $(seq 1 120); do
    [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 2
  done
fi

echo "==> 安装 APK"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null

if [ -f "$SAMPLE" ]; then
  echo "==> 推入示例文件"
  "$ADB" shell mkdir -p /sdcard/Download/orgtd
  "$ADB" push "$SAMPLE" /sdcard/Download/orgtd/gtd.org >/dev/null
fi

"$ADB" shell am start -n dev.orgtd/.MainActivity >/dev/null

cat <<'EOF'

模拟器窗口应该已经出现在桌面上。首次使用需要在窗口里授权一次目录：

    「选择目录」→ Downloads → orgtd → 使用此文件夹 → 允许

之后每次运行本脚本只会重新安装 APK，目录授权会保留。
EOF
