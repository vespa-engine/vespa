#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $BUILDKITE != true ]]; then
    echo "Skipping artifact publishing when not executed by Buildkite."
    exit 0
fi

cd "$WORKDIR/artifacts/$ARCH"

tar -cf rpm-repo.tar rpms &
tar -cf maven-repo.tar maven-repo
cp -a rpms/vespa-config-model-fat-*.rpm .
wait

for FILE in *.tar *.rpm; do
    cosign sign-blob -y --oidc-provider=buildkite-agent --output-signature "$FILE.sig" --output-certificate "$FILE.pem" "$FILE"
done

buildkite-agent artifact upload "*.tar;*.tar.sig;*.tar.pem;*.rpm;*.rpm.sig;*.rpm.pem" "$BUILDKITE_ARTIFACT_DESTINATION/$VESPA_VERSION/artifacts/$ARCH"
