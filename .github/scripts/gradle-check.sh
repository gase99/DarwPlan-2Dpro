#!/usr/bin/env bash
# Keep a failed build failed, while surfacing useful diagnostics through the Checks API.
set -uo pipefail
log="${RUNNER_TEMP:-/tmp}/cabinet-gradle.log"
set +e
./gradlew "$@" --stacktrace 2>&1 | tee "$log"
result=${PIPESTATUS[0]}
if [ "$result" -ne 0 ]; then
  message=$( { grep -E '^e:|^\* What went wrong:|^> (Execution|There|Could|A |Compilation|Unable)|^.*error:|FAILED|tests completed' "$log" | head -60; tail -35 "$log"; } )
  message="${message//'%'/'%25'}"
  message="${message//$'\r'/'%0D'}"
  message="${message//$'\n'/'%0A'}"
  echo "::error title=Gradle check failed::$message"
fi
python3 .github/scripts/test-summary.py
exit "$result"
