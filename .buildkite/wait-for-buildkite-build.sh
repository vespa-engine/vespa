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

if [[ $# -lt 3 ]]; then
    echo "Usage: $0 <Buildkite build URL> <Buildkite web URL> <Timeout sec>"
    exit 1
fi

URL=$1
WEB_URL=$2
TIMEOUT=$3

echo "--- ⏱️ Waiting for Buildkite build completion"
echo "Monitoring build: $WEB_URL"
echo "Timeout: ${TIMEOUT}s"

WAIT_UNTIL=$(( $(date +%s) + "$TIMEOUT" ))
while [[ $(date +%s) -le $WAIT_UNTIL ]]; do
    STATUS=$(curl -sSL -H "Content-Type: application/json" -H "Authorization: Bearer $BUILDKITE_TRIGGER_TOKEN" "$URL" | jq -re '.state')

    echo "Status for $URL is $STATUS"

    case $STATUS in
        passed)
            echo "✅ Build completed successfully"
            break
            ;;
        failed)
            echo "❌ Buildkite build failed. Visit $WEB_URL for information."
            exit 1
            ;;
        *)
            sleep 5
            ;;
    esac
done

if [[ $STATUS != passed ]]; then
    echo "⏰ Timed out waiting for build at $WEB_URL ."
    exit 1
fi
