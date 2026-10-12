#!/bin/sh
# API 호스팅 배포 준비: src 복사 후 /tmp/jupjup-api 에서 vercel --prod
# 필요 env (dashboard 또는 vercel env add): DATABASE_URL, INGEST_TOKEN, ALLOW_ORIGIN
set -eu
SRC="$(cd "$(dirname "$0")/.." && pwd)"
DST="${DST:-/tmp/jupjup-api}"
rm -rf "$DST"
mkdir -p "$DST/api"
cp -r "$SRC/src" "$DST/"
cp "$SRC/api-host/api/[...route].js" "$DST/api/"
cp "$SRC/api-host/package.json" "$DST/"
cd "$DST" && npm install --no-audit --no-fund 2>&1 | tail -1
echo "ready: $DST"
echo "next: cd $DST && vercel --prod"
