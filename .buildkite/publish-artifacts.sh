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

echo "--- 📤 Publishing build artifacts"
cd "$WORKDIR/artifacts/$ARCH"

echo "Creating archives..."
tar -cf rpm-repo.tar rpms &
tar -cf maven-repo.tar maven-repo
cp -a rpms/vespa-config-model-fat-*.rpm .
wait

echo "Signing artifacts..."
for FILE in *.tar *.rpm; do
    cosign sign-blob -y --oidc-provider=buildkite-agent --output-signature "$FILE.sig" --output-certificate "$FILE.pem" "$FILE"
done

ARTIFACT_DESTINATION="${VESPA_ENGINE_ARTIFACTS_BUCKET}/${VESPA_ENGINE_ARTIFACTS_PREFIX}/${VESPA_VERSION}/artifacts/${ARCH}"
echo "📦 Uploading artifacts to ${ARTIFACT_DESTINATION} ..."
buildkite-agent artifact upload "*.tar;*.tar.sig;*.tar.pem;*.rpm;*.rpm.sig;*.rpm.pem" "$ARTIFACT_DESTINATION"

# FIXME(marlon): Remove multiple upload in the transition period.
#
# For the specific case of vespa8/alma8, we also upload to the old path without the "alma8" segment
# to maintain backward compatibility.
if [[ "${VESPA_ENGINE_ARTIFACTS_PREFIX}" == "vespa8/alma8" ]]; then
    ARTIFACT_DESTINATION="${VESPA_ENGINE_ARTIFACTS_BUCKET}/${VESPA_VERSION}/artifacts/${ARCH}"
    echo "ℹ️ Backward compatibility upload to ${ARTIFACT_DESTINATION} ..."
    buildkite-agent artifact upload "*.tar;*.tar.sig;*.tar.pem;*.rpm;*.rpm.sig;*.rpm.pem" "$ARTIFACT_DESTINATION"
fi
