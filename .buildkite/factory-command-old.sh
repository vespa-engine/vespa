#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -eo pipefail

if (( $# < 1 )); then
    echo "Usage: $0 <command> [options]"
    exit 1
fi

COMMAND=$1
FACTORY_API="https://factory.vespa.aws-us-east-1a.vespa.oath.cloud/api/factory/v1"
COOKIEJAR=$(pwd)/jar.txt
# shellcheck disable=2064
trap "rm -f $COOKIEJAR" EXIT

SESSION_TOKEN=null
WAIT_UNTIL=$(( $(date +%s) + 120 ))
set +e
while [[ $SESSION_TOKEN == null ]]; do
  SESSION_TOKEN=$(curl -s -H 'Content-Type: application/json' -H 'Accept: application/json' -d "{ \"username\": \"svc-okta-vespa-factory\", \"password\": \"$SVC_OKTA_VESPA_FACTORY_TOKEN\" }" https://ouryahoo.okta.com/api/v1/authn | jq -re '.sessionToken')

  if [[ $SESSION_TOKEN == null ]]; then
    if [[ $(date +%s) -ge $WAIT_UNTIL ]]; then
      echo "Could not fetch session token from Okta: SESSION_TOKEN=$SESSION_TOKEN"
      exit 1
    else
      echo "Invalid SESSION_TOKEN=$SESSION_TOKEN . Trying again ..." >&2
      sleep 3
    fi
  fi
done
set -e

LOCATION=$(curl -s -i -c "$COOKIEJAR" "https://factory.vespa.aws-us-east-1a.vespa.oath.cloud/login" | grep location | awk '{print $2}' | tr -d '\r')
curl -sL -b "$COOKIEJAR" -c "$COOKIEJAR" "$LOCATION&sessionToken=$SESSION_TOKEN" &> /dev/null

CURL="curl -sL -b $COOKIEJAR"

shift
case $COMMAND in
  get-version)
    VERSION=$1
    if [[ -z $VERSION ]]; then echo "Usage: $0 $COMMAND <version>"; exit 1; fi
    $CURL "$FACTORY_API/versions/$VERSION"
    ;;
  create-build)
    FACTORY_PIPELINE_ID=$1
    FACTORY_PLATFORM=$2
    if [[ -z $FACTORY_PIPELINE_ID ]]; then echo "Usage: $0 $COMMAND <pipeline id> [factory platform]"; exit 1; fi
    if [[ -z $FACTORY_PLATFORM ]]; then FACTORY_PLATFORM="opensource_centos7"; fi
    $CURL -d "{
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
    $CURL -d "{
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
    $CURL -d "{
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
    $CURL -d "{
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
