#!/usr/bin/env bash
# Package locally and run an independent consumer; never uploads or changes refs.
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
cd "$repo"
mkdir -p build/distribution-verification
stage=$(mktemp -d "$repo/build/distribution-verification/candidate.XXXXXX")
version=$(sed -n 's/^version=//p' gradle.properties)
cat > "$stage/local-publish.gradle" <<'INIT'
allprojects {
    // Local unsigned inspection only. The release signing configuration is untouched.
    tasks.withType(org.gradle.plugins.signing.Sign).configureEach { enabled = false }
}
INIT
./gradlew publishToMavenLocal "-Dmaven.repo.local=$stage/maven" -I "$stage/local-publish.gradle" "$@"
python3 tools/inspect-distribution.py "$stage/maven" "$version" > "$stage/artifacts.sha256"
mkdir -p "$stage/consumer"
cp tests/distribution/{settings.gradle.kts,build.gradle.kts,gradle.properties} "$stage/consumer/"
cp -R tests/distribution/src tests/distribution/gradle "$stage/consumer/"
./gradlew -p "$stage/consumer" ktlintCheck test dependencies \
    "-PcandidateRepository=$stage/maven" "-PcandidateVersion=$version" \
    "-PcontractDirectory=$repo/contracts" "$@"
printf 'Verified candidate: %s\nArtifact manifest: %s\n' "$stage" "$stage/artifacts.sha256"
