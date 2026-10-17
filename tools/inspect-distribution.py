"""Validate fresh Maven publications and emit a deterministic artifact manifest."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
version = sys.argv[2]
modules = ["core", "openai", "ollama", "anthropic", "realtime-openai", "websearch-serpapi"]
coordinates = root / "com/vetzal/mojentic"
expected = {f"mojentic-{module}{suffix}" for module in modules for suffix in ("", "-jvm", "-android")}
assert {p.name for p in coordinates.iterdir()} == expected, "Unexpected publication targets (including removed iOS)"
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
for path in sorted(coordinates.rglob("*")):
    if not path.is_file() or path.name == "maven-metadata-local.xml":
        continue
    if path.suffix == ".pom":
        pom = ET.parse(path).getroot()
        assert pom.findtext("m:groupId", namespaces=ns) == "com.vetzal.mojentic"
        assert pom.findtext("m:version", namespaces=ns) == version
        for dep in pom.findall("m:dependencies/m:dependency", ns):
            assert dep.findtext("m:version", namespaces=ns), "Unversioned dependency"
    if path.suffix == ".module":
        metadata = json.loads(path.read_text())
        assert metadata["component"]["version"] == version
        assert "ios" not in path.read_text().lower(), "Removed iOS variant still published"
    if path.suffix in (".pom", ".module", ".jar", ".aar"):
        print(hashlib.sha256(path.read_bytes()).hexdigest(), " ", path.relative_to(root), sep="")
