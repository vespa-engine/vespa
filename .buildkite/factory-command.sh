#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Executes Factory API commands based on the provided arguments.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

if (( $# < 1 )); then
    echo "Usage: $0 <command> [options]"
    exit 1
fi

COMMAND=$1
FACTORY_API="https://api.factory.vespa.ai/factory/v1"

IDENTITY_PATH="${ATHENZ_KEY_AND_CERT_PATH:-/workspace/identity}"
CURL="curl -sL --key $IDENTITY_PATH/key --cert $IDENTITY_PATH/cert"
TOKEN=$(curl -sL --key "$IDENTITY_PATH/key" --cert "$IDENTITY_PATH/cert" -X POST -H "Content-Type: application/x-www-form-urlencoded" -d"grant_type=client_credentials&scope=vespa.factory%3Adomain" "https://zts.athenz.vespa-cloud.com:4443/zts/v1/oauth2/token" | jq -re '.access_token')

shift
case $COMMAND in
  get-version)
    VERSION="${1-}"
    if [[ -z $VERSION ]]; then echo "Usage: $0 $COMMAND <version>"; exit 1; fi
    $CURL -H "Authorization: Bearer $TOKEN" "$FACTORY_API/versions/$VERSION"
    ;;
  create-build)
    FACTORY_PIPELINE_ID="${1-}"
    FACTORY_PLATFORM="${2-}"
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
    SYSTEM_NAME="${1:-opensource}"
    $CURL -H "Authorization: Bearer $TOKEN" -d "{
        \"startSeconds\": $(date +%s),
        \"systemName\": \"$SYSTEM_NAME\"
    }" \
    "$FACTORY_API/releases"
    ;;
  update-build-status)
    FACTORY_PIPELINE_ID="${1-}"
    STATUS="${2-}"
    DESCRIPTION="${3-}"
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
    VERSION="${1-}"
    SYSTEM_NAME="${2:-opensource}"
    if [[ -z $VERSION ]]; then echo "Usage: $0 $COMMAND <version> [<systemName>]"; exit 1; fi
    $CURL -H "Authorization: Bearer $TOKEN" -d "{
        \"releasedSeconds\": $(date +%s),
        \"systemName\": \"${SYSTEM_NAME}\"
    }" \
    "$FACTORY_API/releases/$VERSION"
    ;;
  update-buildkite-job-run)
    START_SECONDS="${1-}"
    FACTORY_PIPELINE_ID="${2-}"
    STATUS="${3-}"
    if [[ -z $START_SECONDS ]] || [[ -z $FACTORY_PIPELINE_ID ]] || [[ -z $STATUS ]]; then
      echo "Usage: $0 $COMMAND <start seconds> <pipeline id> <status>"
      exit 1
    fi
    FACTORY_BUILD_NUMBER=$(( FACTORY_PIPELINE_ID << 32 | BUILDKITE_BUILD_NUMBER & 0xFFFFFF ))
    $CURL -H "Authorization: Bearer $TOKEN" -d "{
        \"startSeconds\": $START_SECONDS,
        \"apiUrl\": \"https://api.buildkite.com/\",
        \"pipelineId\": $FACTORY_PIPELINE_ID,
        \"jobId\": 0,
        \"buildId\": $FACTORY_BUILD_NUMBER,
        \"status\": \"$STATUS\"
    }" \
    "$FACTORY_API/job-runs"
    ;;
  update-github-job-run)
    # TODO: remove this when we have vespa-factory-cli in the build image (this must be implemented in the cli)
    START_SECONDS="${1-}"
    GITHUB_API_URL="${2-}"        # STRING, used to lookup ScrewdriverInstance
    WORKFLOW_NAME="${3-}"         # STRING, used to lookup ScrewdriverPipeline
    JOB_NAME="${4-}"              # STRING, used to lookup crewdriverJob
    JOB_ID="${5-}"                # LONG, github's numeric job id, used as the build id in factory
    WEB_URL="${6-}"               # STRING, the URL to the job in the github UI
    STATUS="${7-}"
    if [[ -z $START_SECONDS ]] || [[ -z $GITHUB_API_URL ]] ||  [[ -z $WORKFLOW_NAME ]] || \
       [[ -z $JOB_NAME ]] || [[ -z $JOB_ID ]] || [[ -z $WEB_URL ]] || [[ -z $STATUS ]]; then
      echo "Usage: $0 $COMMAND <start seconds> <github api url> <workflow name> <job name> <job id> <web url> <status>"
      exit 1
    fi
    $CURL -H "Authorization: Bearer $TOKEN" -d "{
        \"startSeconds\": $START_SECONDS,
        \"apiUrl\": \"$GITHUB_API_URL\",
        \"pipelineName\": \"$WORKFLOW_NAME\",
        \"jobName\": \"$JOB_NAME\",
        \"buildId\": $JOB_ID,
        \"webUrl\": \"$WEB_URL\",
        \"status\": \"$STATUS\"
    }" \
    "$FACTORY_API/job-runs"
    ;;
  *)
    echo "Unknown command $COMMAND"
    exit 1
    ;;
esac
