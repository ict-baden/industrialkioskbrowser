#!/usr/bin/env bash
# Vergleicht die Bibliotheken der Release-APK mit der Liste „Components“ in THIRD-PARTY-NOTICES.txt.
# Neue oder geänderte Abhängigkeit → Liste und lesbaren Teil der Datei nachziehen.
set -euo pipefail
cd "$(dirname "$0")/../.."

actual=$(./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath --console=plain \
  | grep -oE '[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:[0-9][^ ]*( -> [0-9][^ ]*)?' \
  | sed -E 's/:[^:]+ -> /:/' | grep -v -- '-bom:' | sort -u)
listed=$(sed -n '/^Components (checked by the CI)/,/^-----/p' THIRD-PARTY-NOTICES.txt \
  | grep -oE '^   [a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:[^ ]+' | tr -d ' ' | sort -u)

if ! diff <(echo "$listed") <(echo "$actual"); then
  echo "::error::THIRD-PARTY-NOTICES.txt passt nicht zu den Bibliotheken der APK (< nur in der Datei, > nur in der APK)"
  exit 1
fi
echo "THIRD-PARTY-NOTICES.txt vollständig: $(echo "$actual" | wc -l | tr -d ' ') Bibliotheken"
# Für die SBOM (.github/scripts/sbom.py)
mkdir -p build && echo "$actual" > build/release-dependencies.txt
