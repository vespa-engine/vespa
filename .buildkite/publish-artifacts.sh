#!/bin/bash

set -euo pipefail

if [[ $BUILDKITE != true ]]; then
    echo "Skipping artifact publishing when not executed by Buildkite."
    exit 0
fi

cd "$WORKDIR/artifacts/$ARCH"

tar -cf rpm-repo.tar rpms &
tar -cf maven-repo.tar maven-repo
wait

for FILE in *.tar; do
    cosign sign-blob -y --oidc-provider=buildkite-agent --output-signature "$FILE.sig" --output-certificate "$FILE.pem" "$FILE"
done

buildkite-agent artifact upload "*.tar;*.tar.sig;*.tar.pem" "$BUILDKITE_ARTIFACT_DESTINATION/$VESPA_VERSION"
