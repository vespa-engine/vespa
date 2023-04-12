#!/bin/bash
set -eo pipefail

if (( $# < 1 )); then
    echo "Usage: $0 <command> [options]"
    exit 1
fi

COMMAND=$1
FACTORY_API="https://factory.vespa.aws-us-east-1a.vespa.oath.cloud/api/factory/v1"
COOKIEJAR=$(pwd)/jar.txt
trap "rm -f $COOKIEJAR" EXIT

SESSION_TOKEN=$(curl -s -H 'Content-Type: application/json' -H 'Accept: application/json' -d "{ \"username\": \"svc-okta-vespa-factory\", \"password\": \"$SVC_OKTA_VESPA_FACTORY_TOKEN\" }" https://ouryahoo.okta.com/api/v1/authn | jq -re '.sessionToken')
LOCATION=$(curl -s -i -c $COOKIEJAR "https://factory.vespa.aws-us-east-1a.vespa.oath.cloud/login" | grep location | awk '{print $2}' | tr -d '\r')
curl -sL -b $COOKIEJAR -c $COOKIEJAR "$LOCATION&sessionToken=$SESSION_TOKEN" &> /dev/null

CURL="curl -sL -b $COOKIEJAR"

shift
case $COMMAND in
  get-version)
    VERSION=$1
    if [[ -z $VERSION ]]; then echo "Usage: $0 $COMMAND <version>"; exit 1; fi
    $CURL "$FACTORY_API/versions/$VERSION"
    ;;
  create-build)
    $CURL -d "{
        \"startSeconds\": $(date +%s),
        \"sdApiUrl\": \"https://api.screwdriver.cd/v4/\",
        \"pipelineId\": $SD_PIPELINE_ID,
        \"jobId\": $SD_JOB_ID,
        \"buildId\": $SD_BUILD_ID,
        \"platform\": \"opensource_centos7\"
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
    STATUS=$1
    DESCRIPTION=$2
    if [[ -z $STATUS ]] || [[ -z $DESCRIPTION ]]; then echo "Usage: $0 $COMMAND <status> <description>"; exit 1; fi
    $CURL -d "{
        \"updatedSeconds\": $(date +%s),
        \"sdApiUrl\": \"https://api.screwdriver.cd/v4/\",
        \"pipelineId\": $SD_PIPELINE_ID,
        \"jobId\": $SD_JOB_ID,
        \"buildId\": $SD_BUILD_ID,
        \"status\": \"$STATUS\",
        \"description\": \"$DESCRIPTION\"
    }" \
    "$FACTORY_API/builds/$SD_BUILD_ID/status"
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
