"""Schreibt eine CycloneDX-SBOM (JSON) der Release-APK.

Quelle ist die Liste group:artifact:version, die check-notices.sh aus releaseRuntimeClasspath erzeugt –
dieselbe Liste, gegen die THIRD-PARTY-NOTICES.txt geprüft wird.

    python3 .github/scripts/sbom.py <version> build/release-dependencies.txt dist/IndustrialKioskBrowser-<version>.cdx.json
"""
import datetime
import json
import sys
import uuid

version, deps_file, out_file = sys.argv[1:4]
components = []
with open(deps_file, encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        group, artifact, ver = line.split(":", 2)
        components.append({
            "type": "library",
            "bom-ref": f"pkg:maven/{group}/{artifact}@{ver}",
            "group": group,
            "name": artifact,
            "version": ver,
            "purl": f"pkg:maven/{group}/{artifact}@{ver}",
        })

app_ref = f"pkg:android/de.ictbaden.industrialkioskbrowser@{version}"
bom = {
    "bomFormat": "CycloneDX",
    "specVersion": "1.5",
    "serialNumber": f"urn:uuid:{uuid.uuid4()}",
    "version": 1,
    "metadata": {
        "timestamp": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "component": {
            "type": "application",
            "bom-ref": app_ref,
            "name": "IndustrialKioskBrowser",
            "version": version,
            "licenses": [{"license": {"id": "MIT"}}],
            "supplier": {"name": "ICT-Baden", "url": ["https://ict-baden.de/"]},
        },
    },
    "components": components,
    "dependencies": [{"ref": app_ref, "dependsOn": [c["bom-ref"] for c in components]}],
}
with open(out_file, "w", encoding="utf-8") as f:
    json.dump(bom, f, indent=2)
print(f"SBOM: {len(components)} Komponenten -> {out_file}")
