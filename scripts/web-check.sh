#!/usr/bin/env bash
# Build the TeaVM (web) bundle and smoke-test it in headless Firefox.
# Usage: scripts/web-check.sh [--play] [--query=...] [--out=/abs/path.png]
#   --play   start straight in a level (URL query ?seed=42&play) and check it runs without exceptions.
# Env:  WEB_XINT=1  run the TeaVM worker with -Xint (slow, ~4 min). Only a fallback: the 2026-11-04
#                   JIT crashes turned out to be faulty RAM; the normal build takes ~25 s.
# Prints the smoke result; exit code 0 = PASS. Screenshot path is printed, inspect it.
set -uo pipefail
cd "$(dirname "$0")/.."

play=0; query="seed=42&play"; out="${TMPDIR:-/tmp}/star-trap-web.png"
for a in "$@"; do
  case "$a" in
    --play) play=1 ;;
    --query=*) query="${a#--query=}"; play=1 ;;
    --out=*) out="${a#--out=}" ;;
  esac
done

./gradlew teavm:teavmClasses --console=plain -q || { echo "RESULT: FAIL (compile)"; exit 1; }
if [ "${WEB_XINT:-0}" = 1 ]; then
  # teavm:clean: the incremental build can keep a stale reflection class list, so a new component class fails only at runtime (seen 2026-11-07).
  JAVA_TOOL_OPTIONS=-Xint ./gradlew --no-daemon teavm:clean teavm:generateJavaScript --console=plain -q
else
  ./gradlew teavm:clean teavm:generateJavaScript --console=plain -q
fi || { echo "RESULT: FAIL (TeaVM generateJavaScript; if the log shows a JVM SIGSEGV retry with WEB_XINT=1)"; rm -f hs_err_pid*.log; exit 1; }

if [ "$play" = 1 ]; then
  node scripts/web-smoke.mjs --wait=20 --query="$query" --out="$out"
else
  node scripts/web-smoke.mjs --wait=15 --out="$out"
fi
