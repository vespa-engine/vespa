#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -o pipefail
set -o nounset
set -o errexit
set -o xtrace

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa release version>"
    exit 1
fi

if [[ -z $OSSRH_USER ]] || [[ -z $OSSRH_TOKEN ]]  || [[ -z $GPG_KEYNAME ]] || [[ -z $GPG_PASSPHRASE_TOKEN ]] || [[ -z $GPG_ENCPHRASE_TOKEN ]]; then
    echo -e "The follwing env variables must be set:\n OSSRH_USER\n OSSRH_TOKEN\n GPG_KEYNAME\n GPG_PASSPHRASE_TOKEN\n GPG_ENCPHRASE_TOKEN"
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
MAVEN_GPG_PASSPHRASE=$GPG_PASSPHRASE_TOKEN
export MVN
export MVN_OPTS
export MAVEN_GPG_PASSPHRASE

TMP_STAGING=$(mktemp -d)
export TMP_STAGING
mkdir -p "$TMP_STAGING"
# shellcheck disable=2064
trap "rm -rf $TMP_STAGING" EXIT

sign_module() {

    #Debug
    set -x

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

#Debug
set -x

aws s3 cp "s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar" .
aws s3 cp "s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar.pem" .
aws s3 cp "s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar.sig" .
cosign verify-blob --certificate-identity https://buildkite.com/vespaai/vespa-engine-vespa --certificate-oidc-issuer https://agent.buildkite.com --signature maven-repo.tar.sig --certificate maven-repo.tar.pem maven-repo.tar
rm -rf maven-repo
tar xvf maven-repo.tar
REPO_ROOT=$(pwd)/maven-repo

cd "$REPO_ROOT"
find . -name "$VESPA_RELEASE" -type d | sed 's,^./,,' | xargs -n 1 -P $NUM_PROC -I '{}' bash -c "sign_module {}"

# shellcheck disable=2086
$MVN $MVN_OPTS --settings="$SOURCE_DIR/.buildkite/settings-publish.xml" \
    org.sonatype.central:central-publishing-maven-plugin:0.7.0:publish \
    -DrepositoryDirectory="$TMP_STAGING" \
    -DpublishingServerId=central \
    -DautoPublish=true \
    -DwaitUntil=published
