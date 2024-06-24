#!/bin/bash

set -euo pipefail

if [[ $BUILDKITE != true ]]; then
    echo "Skipping artifact publishing when not executed by Buildkite."
    exit 0
fi

if [[ $(arch) == x86_64 ]]; then
    JAVA_TEST_TOKEN=$UNIT_TEST_JAVA_AMD64_TOKEN
    CPP_TEST_TOKEN=$UNIT_TEST_CPP_AMD64_TOKEN
else
    JAVA_TEST_TOKEN=$UNIT_TEST_JAVA_ARM64_TOKEN
    CPP_TEST_TOKEN=$UNIT_TEST_CPP_ARM64_TOKEN
fi

if [[ -z $JAVA_TEST_TOKEN ]]; then
    echo "Missing JAVA_TEST_TOKEN. Exiting."
    exit 1
fi
if [[ -z $CPP_TEST_TOKEN ]]; then
    echo "Missing CPP_TEST_TOKEN. Exiting."
    exit 1
fi

upload_result() {
    curl \
        -X POST \
        -H "Authorization: Token token=\"$BUILDKITE_ANALYTICS_TOKEN\"" \
        -F "data=@$1" \
        -F "format=junit" \
        -F "run_env[CI]=buildkite" \
        -F "run_env[key]=$BUILDKITE_BUILD_ID" \
        -F "run_env[url]=$BUILDKITE_BUILD_URL" \
        -F "run_env[branch]=$BUILDKITE_BRANCH" \
        -F "run_env[commit_sha]=$BUILDKITE_COMMIT" \
        -F "run_env[number]=$BUILDKITE_BUILD_NUMBER" \
        -F "run_env[job_id]=$BUILDKITE_JOB_ID" \
        -F "run_env[message]=$BUILDKITE_MESSAGE" \
        "https://analytics-api.buildkite.com/v1/uploads"
}

export -f upload_result

# Upload all surefire TEST-*.xml reports
cd "$WORKDIR"
export BUILDKITE_ANALYTICS_TOKEN=$JAVA_TEST_TOKEN
# shellcheck disable=2038
find . -name "TEST-*.xml" -type f | xargs -n 1 -P 50 -I '{}' bash -c "upload_result {}"

# Upload the cpp test report
export BUILDKITE_ANALYTICS_TOKEN=$CPP_TEST_TOKEN
upload_result "$LOG_DIR/vespa-cpptest-results.xml"

