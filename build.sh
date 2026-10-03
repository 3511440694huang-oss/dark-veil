#!/usr/bin/env bash
# dark-veil（暗纱）构建脚本 —— 在 proot Ubuntu 环境执行
#
# 用法：
#   bash build.sh                          # 构建 debug APK（默认）
#   bash build.sh :app:compileDebugKotlin  # 只过 Kotlin 编译（最快暴露代码错误）
#   bash build.sh :app:assembleRelease     # 构建 release APK
#   bash build.sh clean                    # 清理构建目录
#
# 说明：环境变量与日志已固化，构建日志落盘 /tmp/dark-veil-build.log。
set -u

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/root/.gradle}"
export GRADLE_OPTS="-Dorg.gradle.daemon=false -Djava.io.tmpdir=/tmp"
export ANDROID_HOME=/storage/emulated/0/WORK/02-工具链/android-sdk
export ANDROID_USER_HOME=/storage/emulated/0/WORK/02-工具链/android-user-home

PROJ_DIR="/storage/emulated/0/WORK/01-开发项目/护眼/dark-veil"
GRADLE_BIN="/storage/emulated/0/WORK/02-工具链/toolchain/gradle-8.11.1/bin/gradle"
LOG="/tmp/dark-veil-build.log"

if [ $# -eq 0 ]; then
  set -- :app:assembleDebug
fi

cd "$PROJ_DIR" || exit 1

bash "$GRADLE_BIN" "$@" \
  -Pandroid.aapt2FromMavenOverride=/root/aapt2-bin/aapt2 \
  2>&1 | tee "$LOG"

BUILD_STATUS=${PIPESTATUS[0]}
echo "BUILD EXIT=$BUILD_STATUS"
exit "$BUILD_STATUS"
