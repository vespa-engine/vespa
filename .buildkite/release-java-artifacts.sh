#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Documentation for endpoints on Central API:
# - https://central.sonatype.org/publish/publish-portal-api/
#


set -o pipefail
set -o nounset
set -o errexit
set -o xtrace

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa release version>"
    exit 1
fi

# Helper functions to control debug output for sensitive operations
disable_debug() {
    set +x
}
enable_debug() {
    set -x
}
export -f disable_debug
export -f enable_debug

if [[ -z $MVN_CENTRAL_USER ]] || [[ -z $MVN_CENTRAL_TOKEN ]]  || [[ -z $GPG_KEYNAME ]] || [[ -z $GPG_PASSPHRASE_TOKEN ]] || [[ -z $GPG_ENCPHRASE_TOKEN ]]; then
    echo -e "The following env variables must be set:\n MVN_CENTRAL_USER\n MVN_CENTRAL_TOKEN\n GPG_KEYNAME\n GPG_PASSPHRASE_TOKEN\n GPG_ENCPHRASE_TOKEN"
    exit 1
fi

readonly VESPA_RELEASE="$1"

# shellcheck shell=bash disable=SC1083
QUERY_VERSION_HTTP_CODE=$(curl --write-out %{http_code} --silent --location --output /dev/null "https://repo.maven.apache.org/maven2/com/yahoo/vespa/parent/${VESPA_RELEASE}/parent-${VESPA_RELEASE}.pom")
if [[ "200" == "$QUERY_VERSION_HTTP_CODE" ]]; then
  echo "Vespa version $VESPA_RELEASE is already promoted, exiting"
  exit 0
fi

SOURCE_DIR=$(pwd)
export SOURCE_DIR

mkdir -p "$SOURCE_DIR/.buildkite/deploy"
# gpg-agent in RHEL 8 runs out of memory if we use Maven and sign in parallel. Add option to overcome this.
echo "auto-expand-secmem" >> "$SOURCE_DIR/.buildkite/deploy/gpg-agent.conf"
openssl aes-256-cbc -md md5 -pass "pass:$GPG_ENCPHRASE_TOKEN" -in "$SOURCE_DIR/.buildkite/deploy/pubring.gpg.enc" -out "$SOURCE_DIR/.buildkite/deploy/pubring.gpg" -d
openssl aes-256-cbc -md md5 -pass "pass:$GPG_ENCPHRASE_TOKEN" -in "$SOURCE_DIR/.buildkite/deploy/secring.gpg.enc" -out "$SOURCE_DIR/.buildkite/deploy/secring.gpg" -d
chmod 700 "$SOURCE_DIR/.buildkite/deploy"
chmod 600 "$SOURCE_DIR"/.buildkite/deploy/*
# shellcheck shell=bash disable=SC2064
trap "rm -rf $SOURCE_DIR/.buildkite/deploy" EXIT

# Number of parallel uploads
NUM_PROC=10

MVN=${MVN:-mvn}
MVN_OPTS=${MVN_OPTS:-}
disable_debug
MAVEN_GPG_PASSPHRASE=$GPG_PASSPHRASE_TOKEN
enable_debug

export MVN
export MVN_OPTS
export MAVEN_GPG_PASSPHRASE

disable_debug
# Build the Base64‑encoded credentials for the Portal API
CENTRAL_AUTH_TOKEN=$(printf "%s:%s" "$MVN_CENTRAL_USER" "$MVN_CENTRAL_TOKEN" | base64)
export CENTRAL_AUTH_TOKEN
enable_debug

TMP_STAGING=$(mktemp -d)
export TMP_STAGING
mkdir -p "$TMP_STAGING"

# shellcheck disable=2064
trap "rm -rf $TMP_STAGING" EXIT

sign_module() {
    enable_debug
    ECHO=""

    P=$1
    V=$(basename "$P")
    A=$(basename "$(dirname "$P")")
    G=$(dirname "$(dirname "$P")" | sed 's,/,.,g')
    POM="$P/$A-$V.pom"
    JAR="$P/$A-$V.jar"
    if [[ -f $JAR ]]; then
        FILE_OPTS="-Dfile=$JAR -Dpackaging=jar"
    else
        FILE_OPTS="-Dfile=$POM -Dpackaging=pom"
    fi

    AFILES=()
    ATYPES=()
    ACLASSIFIERS=()
    # shellcheck disable=2044
    for EX in $(find "$P" \( -name "$A-$V-*.jar" -or -name "$A-$V-*.zip" \) ); do
        AFILES+=("$EX")
        EXB=$(basename "$EX")
        EXT=${EXB##*.}
        ATYPES+=("$EXT")
        EXF=${EXB%.*}
        # shellcheck disable=2001
        EXC=$(echo "$EXF"|sed "s,$A-$V-,,")
        ACLASSIFIERS+=("$EXC")
    done

    if (( ${#AFILES[@]} > 0 )); then
        # shellcheck disable=2001
        EXTRA_FILES_OPTS="-Dfiles=$(echo "${AFILES[@]}" | sed 's/\ /,/g') -Dtypes=$(echo "${ATYPES[@]}" | sed 's/\ /,/g') -Dclassifiers=$(echo "${ACLASSIFIERS[@]}" | sed 's/\ /,/g')"
    fi

    # shellcheck disable=2086
    $ECHO $MVN --settings="$SOURCE_DIR/.buildkite/settings-publish.xml" \
          $MVN_OPTS gpg:sign-and-deploy-file \
          -Durl="file://$TMP_STAGING" \
          -DrepositoryId=maven-central \
          -DgroupId="$G" \
          -DartifactId="$A" \
          -Dversion="$V" \
          -DpomFile="$POM" \
          -DgeneratePom=false \
          $FILE_OPTS \
          $EXTRA_FILES_OPTS

}
export -f sign_module

wait_deployment_reaching_status() {
    local expected_state=$1 ; shift
    local deployment=$1 ; shift
    local START_EPOCH
    local CURRENT_EPOCH
    local ELAPSED_TIME

    # 2. Poll waiting the deployment to reach the desired state
    START_EPOCH=$(date +%s)
    while true; do
    # Example of the JSON response from the status endpoint:
    # {
    #  "deploymentId": "5f52bd32-9403-4e3e-8a40-7a690d985c3f",
    #  "deploymentName": "test",
    #  "deploymentState": "FAILED", # Can be: [ PENDING, VALIDATING, VALIDATED, PUBLISHING, PUBLISHED, FAILED ]
    #  "purls": [],
    #  "errors": {}
    # }
        # Temporarily disable xtrace to avoid leaking auth token
        disable_debug
        STATUS_JSON=$(curl --silent --fail \
        -H "Authorization: Bearer $CENTRAL_AUTH_TOKEN" \
        --request POST \
        "https://central.sonatype.com/api/v1/publisher/status?id=${deployment}")
        # Re-enable xtrace
        enable_debug

        STATE="$(echo "$STATUS_JSON" | jq -r '.deploymentState')"
        echo "Deployment state: $STATE"
        if [[ "$STATE" == "${expected_state}" ]]; then
            echo "Deployment reached expected state: $STATE"
            break
        elif [[ "$STATE" == "FAILED" ]]; then
            echo "Deployment FAILED:"
            echo "$STATUS_JSON"
            exit 1
        fi

        CURRENT_EPOCH=$(date +%s)
        ELAPSED_TIME=$((CURRENT_EPOCH - START_EPOCH))
        if (( ELAPSED_TIME > 600 )); then
            echo "Deployment did not reach expected state within 10 minutes, exiting"
            exit 1
        fi

        echo "Waiting for deployment to reach state: $expected_state, current state: $STATE"
        sleep 20
    done
}
export -f wait_deployment_reaching_status

aws s3 cp "s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar" .
aws s3 cp "s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar.pem" .
aws s3 cp "s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar.sig" .
cosign verify-blob --certificate-identity https://buildkite.com/vespaai/vespa-engine-vespa --certificate-oidc-issuer https://agent.buildkite.com --signature maven-repo.tar.sig --certificate maven-repo.tar.pem maven-repo.tar
rm -rf maven-repo
tar xvf maven-repo.tar
REPO_ROOT=$(pwd)/maven-repo

cd "$REPO_ROOT"
find . -name "$VESPA_RELEASE" -type d | sed 's,^./,,' | xargs -n 1 -P $NUM_PROC -I '{}' bash -c "sign_module {}"

# Create a zip file of all the staged artifacts
ZIP_FILE=$(mktemp).zip
(
  cd "$TMP_STAGING"
  zip -r "$ZIP_FILE" .
)

disable_debug
# Upload the bundle using the Central Portal API
echo "Uploading deployment bundle using Central Portal API"
DEPLOYMENT_ID=$(curl --silent --show-error --fail \
  --retry 3 --retry-delay 60 \
  --request POST \
  --header "Authorization: Bearer $CENTRAL_AUTH_TOKEN" \
  --form "bundle=@$ZIP_FILE" \
  "https://central.sonatype.com/api/v1/publisher/upload?name=Vespa-${VESPA_RELEASE}-release&publishingType=AUTOMATIC")
enable_debug

if [[ -z $DEPLOYMENT_ID ]]; then
    echo "Failed to get deployment ID"
    echo "Response: $DEPLOYMENT_ID"
    exit 1
fi

echo "Got deployment ID: $DEPLOYMENT_ID"
wait_deployment_reaching_status "PUBLISHED" "$DEPLOYMENT_ID"

echo "Deployment $DEPLOYMENT_ID published successfully"
