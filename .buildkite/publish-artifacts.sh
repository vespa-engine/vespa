#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Publishes build artifacts: creates archives, signs them, and uploads to Buildkite.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

: "${VESPA_VERSION:?Environment variable VESPA_VERSION must be set (version to build)}"
: "${LOCAL_RPM_REPO:?Environment variable LOCAL_RPM_REPO must be set (path to local RPM repo)}"
: "${LOCAL_MVN_REPO:?Environment variable LOCAL_MVN_REPO must be set (path to local Maven repo)}"

echo "--- 📤 Publishing build artifacts"
cd "$WORKDIR/artifacts/$ARCH"

echo "Creating archives..."
tar -C "$(dirname "$LOCAL_MVN_REPO")" -cf maven-repo.tar "$(basename "$LOCAL_MVN_REPO")" &
maven_tar_pid=$!
tar -C "$(dirname "$LOCAL_RPM_REPO")" -cf rpm-repo.tar "$(basename "$LOCAL_RPM_REPO")" &
rpm_tar_pid=$!
wait "$maven_tar_pid"
wait "$rpm_tar_pid"

cp -a "${LOCAL_RPM_REPO}"/vespa-config-model-fat-*.rpm .

echo "Signing artifacts..."
for FILE in *.tar *.rpm; do
    cosign sign-blob -y --oidc-provider=buildkite-agent --output-signature "$FILE.sig" --output-certificate "$FILE.pem" "$FILE"
done

ARTIFACT_DESTINATION="${VESPA_ENGINE_ARTIFACTS_BUCKET}/${VESPA_ENGINE_ARTIFACTS_PREFIX}/${VESPA_VERSION}/artifacts/${ARCH}"
echo "📦 Uploading artifacts to ${ARTIFACT_DESTINATION} ..."
buildkite-agent artifact upload "*.tar;*.tar.sig;*.tar.pem;*.rpm;*.rpm.sig;*.rpm.pem" "$ARTIFACT_DESTINATION"
