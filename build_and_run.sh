#!/bin/bash
# usage: ./build_and_run.sh build|test|lint|clean [unit|full|smoke]
# AGENTS.md 6장 빌드 디스패처 — JupJup 통합 (맥줍줍 + 요금줍줍 멀티모듈)
set -e
set -o pipefail
CMD="${1:-build}"
SCOPE="${2:-unit}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/android"

if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
elif [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
fi

case "$CMD" in
  build)
    echo "🔨 assembleDebug (멀티모듈) 빌드 중…"
    # R1: stale APK 오탐 방지 — 빌드 전 기존 산출물 제거
    rm -f app/build/outputs/apk/debug/*.apk
    ./gradlew :app:assembleDebug 2>&1 | tail -5
    APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
    if [ -z "$APK" ]; then
        echo "❌ APK 생성 실패"; exit 1
    fi
    echo "✅ 빌드 완료: $APK"
    DEVICE=$(adb devices 2>/dev/null | grep -w device | awk '{print $1}' | head -1)
    if [ -z "$DEVICE" ]; then
        echo "⚠️  디바이스 연결 없음 (설치 생략)"
    else
        echo "📲 $DEVICE에 설치 중…"
        adb install -r "$APK" 2>&1 | tail -1
    fi
    ;;
  test)
    if [ "$SCOPE" = "full" ]; then
        echo "🧪 전체 테스트 (unit + connected)…"
        echo "⚠️  connected 테스트는 기기 앱을 재설치해 DB가 초기화됩니다 (재수집으로 복구)"
        ./gradlew test connectedAndroidTest 2>&1 | tail -8
    else
        echo "🧪 단위 테스트 (app + services 공통/요금/맥/저널/커뮤니티)…"
        ./gradlew :app:testDebugUnitTest :services:common:testDebugUnitTest :services:mac:testDebugUnitTest :services:plan:testDebugUnitTest :services:promptjournal:testDebugUnitTest :services:community:testDebugUnitTest 2>&1 | tail -8
    fi
    ;;
  lint)
    echo "🔎 lintDebug…"
    ./gradlew lintDebug 2>&1 | tail -8
    ;;
  clean)
    echo "🧹 clean…"
    ./gradlew clean
    ;;
  *)
    echo "usage: $0 build|test|lint|clean [unit|full|smoke]"; exit 1
    ;;
esac
