#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Generates a container tag name based on the provided arguments.

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

echo "Uploading artifacts to Buildkite..."
buildkite-agent artifact upload "*.tar;*.tar.sig;*.tar.pem;*.rpm;*.rpm.sig;*.rpm.pem" "$BUILDKITE_ARTIFACT_DESTINATION/$VESPA_VERSION/artifacts/$ARCH"
