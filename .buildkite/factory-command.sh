#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -eo pipefail

if (( $# < 1 )); then
    echo "Usage: $0 <command> [options]"
    exit 1
fi

COMMAND=$1
FACTORY_API="https://api.factory.vespa.ai/factory/v1"

CURL="curl -sL --key /workspace/identity/key --cert /workspace/identity/cert"
TOKEN=$(curl -sL --key /workspace/identity/key --cert /workspace/identity/cert -X POST -H "Content-Type: application/x-www-form-urlencoded" -d"grant_type=client_credentials&scope=vespa.factory%3Adomain" "https://zts.athenz.vespa-cloud.com:4443/zts/v1/oauth2/token" | jq -re '.access_token')

shift
case $COMMAND in
  get-version)
    VERSION=$1
    if [[ -z $VERSION ]]; then echo "Usage: $0 $COMMAND <version>"; exit 1; fi
    $CURL -H "Authorization: Bearer $TOKEN" "$FACTORY_API/versions/$VERSION"
    ;;
  create-build)
    FACTORY_PIPELINE_ID=$1
    FACTORY_PLATFORM=$2
    if [[ -z $FACTORY_PIPELINE_ID ]]; then echo "Usage: $0 $COMMAND <pipeline id> [factory platform]"; exit 1; fi
    if [[ -z $FACTORY_PLATFORM ]]; then FACTORY_PLATFORM="opensource_centos7"; fi
    $CURL -H "Authorization: Bearer $TOKEN" -d "{
        \"startSeconds\": $(date +%s),
        \"sdApiUrl\": \"https://api.buildkite.com/\",
        \"pipelineId\": $FACTORY_PIPELINE_ID,
        \"jobId\": 0,
        \"buildId\": $BUILDKITE_BUILD_NUMBER,
        \"platform\": \"$FACTORY_PLATFORM\"
    }" \
    "$FACTORY_API/builds"
    ;;
  create-release)
    $CURL -H "Authorization: Bearer $TOKEN" -d "{
        \"startSeconds\": $(date +%s),
        \"systemName\": \"opensource\"
    }" \
    "$FACTORY_API/releases"
    ;;
  update-build-status)
    FACTORY_PIPELINE_ID=$1
    STATUS=$2
    DESCRIPTION=$3
    FACTORY_BUILD_NUMBER=$(( FACTORY_PIPELINE_ID << 32 | BUILDKITE_BUILD_NUMBER & 0xFFFFFF ))
    if [[ -z $FACTORY_PIPELINE_ID ]] || [[ -z $STATUS ]] || [[ -z $DESCRIPTION ]]; then
      echo "Usage: $0 $COMMAND <pipeline id> <status> <description>"
      exit 1
    fi
    $CURL -H "Authorization: Bearer $TOKEN" -d "{
        \"updatedSeconds\": $(date +%s),
        \"sdApiUrl\": \"https://api.buildkite.com/\",
        \"pipelineId\": $FACTORY_PIPELINE_ID,
        \"jobId\": 0,
        \"buildId\": $FACTORY_BUILD_NUMBER,
        \"status\": \"$STATUS\",
        \"description\": \"$DESCRIPTION\"
    }" \
    "$FACTORY_API/builds/$FACTORY_BUILD_NUMBER/status"
    ;;
  update-released-time)
    VERSION=$1
    if [[ -z $VERSION ]]; then echo "Usage: $0 $COMMAND <version>"; exit 1; fi
    $CURL -H "Authorization: Bearer $TOKEN" -d "{
        \"releasedSeconds\": $(date +%s),
        \"systemName\": \"opensource\"
    }" \
    "$FACTORY_API/releases/$VERSION"
    ;;
  *)
    echo "Unknown command $COMMAND"
    exit 1
    ;;
esac
