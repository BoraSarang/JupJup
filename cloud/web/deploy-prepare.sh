#!/bin/sh
# Vercel 정적 배포 준비: mac_web 에셋 복사 + config.js API 베이스 주입.
# 사용: CLOUD_API=https://xxx ./deploy-prepare.sh && cd /tmp/jupjup-web && vercel --prod
set -eu
CLOUD_API="${CLOUD_API:?CLOUD_API 환경변수 필요 (예: https://jupjup-api.example.com)}"
SRC="$(cd "$(dirname "$0")/../../android/services/mac/src/main/assets/mac_web" && pwd)"
DST="${DST:-/tmp/jupjup-web}"
rm -rf "$DST"
mkdir -p "$DST"
cp "$SRC/index.html" "$SRC/app.js" "$SRC/style.css" "$SRC/favicon.svg" "$DST/"
cp "$(dirname "$0")/vercel.json" "$DST/"
sed "s|__CLOUD_API__|${CLOUD_API}|" "$(dirname "$0")/config.js.template" > "$DST/config.js"
node --check "$DST/app.js" && node --check "$DST/config.js"
echo "ready: $DST (CLOUD_API=$CLOUD_API)"
echo "next: cd $DST && vercel --prod"
