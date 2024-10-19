#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa release version>"
    exit 1
fi

if [[ -z $OSSRH_USER ]] || [[ -z $OSSRH_TOKEN ]]  || [[ -z $GPG_KEYNAME ]] || [[ -z $GPG_PASSPHRASE_TOKEN ]] || [[ -z $GPG_ENCPHRASE_TOKEN ]]; then
    echo -e "The follwing env variables must be set:\n OSSRH_USER\n OSSRH_TOKEN\n GPG_KEYNAME\n GPG_PASSPHRASE_TOKEN\n GPG_ENCPHRASE_TOKEN"
    exit 1
fi

readonly VESPA_RELEASE="$1"

QUERY_VERSION_HTTP_CODE=$(curl --write-out %{http_code} --silent --location --output /dev/null https://oss.sonatype.org/content/repositories/releases/com/yahoo/vespa/parent/${VESPA_RELEASE}/parent-${VESPA_RELEASE}.pom)
if [[ "200" == $QUERY_VERSION_HTTP_CODE ]]; then
  echo "Vespa version $VESPA_RELEASE is already promoted, exiting"
  exit 0
fi

export SOURCE_DIR=$(pwd)

mkdir -p $SOURCE_DIR/screwdriver/deploy
# gpg-agent in RHEL 8 runs out of memory if we use Maven and sign in parallel. Add option to overcome this.
echo "auto-expand-secmem" >> $SOURCE_DIR/screwdriver/deploy/gpg-agent.conf
openssl aes-256-cbc -md md5 -pass pass:$GPG_ENCPHRASE_TOKEN -in $SOURCE_DIR/screwdriver/pubring.gpg.enc -out $SOURCE_DIR/screwdriver/deploy/pubring.gpg -d
openssl aes-256-cbc -md md5 -pass pass:$GPG_ENCPHRASE_TOKEN -in $SOURCE_DIR/screwdriver/secring.gpg.enc -out $SOURCE_DIR/screwdriver/deploy/secring.gpg -d
chmod 700 $SOURCE_DIR/screwdriver/deploy
chmod 600 $SOURCE_DIR/screwdriver/deploy/*
trap "rm -rf $SOURCE_DIR/screwdriver/deploy" EXIT

# Number of parallel uploads
NUM_PROC=10

export MVN=${MVN:-mvn}
export MVN_OPTS=${MVN_OPTS:-}
export MAVEN_GPG_PASSPHRASE=$GPG_PASSPHRASE_TOKEN

export TMP_STAGING=$(mktemp -d)
mkdir -p $TMP_STAGING
trap "rm -rf $TMP_STAGING" EXIT

sign_module() {
    ECHO=""

    P=$1
    V=$(basename $P)
    A=$(basename $(dirname $P))
    G=$(dirname $(dirname $P) | sed 's,/,.,g')
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
    for EX in $(find $P \( -name "$A-$V-*.jar" -or -name "$A-$V-*.zip" \) ); do
        AFILES+=($EX)
        EXB=$(basename $EX)
        EXT=${EXB##*.}
        ATYPES+=($EXT)
        EXF=${EXB%.*}
        EXC=$(echo $EXF|sed "s,$A-$V-,,")
        ACLASSIFIERS+=($EXC)
    done

    if (( ${#AFILES[@]} > 0 )); then
        EXTRA_FILES_OPTS="-Dfiles=$(echo ${AFILES[@]} | sed 's/\ /,/g') -Dtypes=$(echo ${ATYPES[@]} | sed 's/\ /,/g') -Dclassifiers=$(echo ${ACLASSIFIERS[@]} | sed 's/\ /,/g')"
    fi

    $ECHO $MVN --settings=$SOURCE_DIR/screwdriver/settings-publish.xml \
          $MVN_OPTS gpg:sign-and-deploy-file \
          -Durl=file://$TMP_STAGING \
          -DrepositoryId=maven-central \
          -DgroupId=$G \
          -DartifactId=$A \
          -Dversion=$V \
          -DpomFile=$POM \
          -DgeneratePom=false \
          $FILE_OPTS \
          $EXTRA_FILES_OPTS

}
export -f sign_module

aws s3 cp  s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar .
aws s3 cp  s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar.pem .
aws s3 cp  s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/amd64/maven-repo.tar.sig .
cosign verify-blob --certificate-identity https://buildkite.com/vespaai/vespa-engine-vespa --certificate-oidc-issuer https://agent.buildkite.com --signature maven-repo.tar.sig --certificate maven-repo.tar.pem maven-repo.tar
rm -rf maven-repo
tar xvf maven-repo.tar
REPO_ROOT=$(pwd)/maven-repo

cd $REPO_ROOT
find . -name "$VESPA_RELEASE" -type d | sed 's,^./,,' | xargs -n 1 -P $NUM_PROC -I '{}' bash -c "sign_module {}"

# Required for the nexus plugin to work with JDK 17
export MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED"

LOGFILE=$(mktemp)
$MVN $MVN_OPTS --settings=$SOURCE_DIR/screwdriver/settings-publish.xml \
    org.sonatype.plugins:nexus-staging-maven-plugin:1.6.14:deploy-staged-repository \
    -DrepositoryDirectory=$TMP_STAGING \
    -DnexusUrl=https://oss.sonatype.org \
    -DserverId=ossrh \
    -DautoReleaseAfterClose=false \
    -DstagingProgressTimeoutMinutes=10 \
    -DstagingProfileId=407c0c3e1a197 | tee $LOGFILE

STG_REPO=$(cat $LOGFILE | grep 'Staging repository at http' | head -1 | awk -F/ '{print $NF}')
$MVN $MVN_OPTS --settings=$SOURCE_DIR/screwdriver/settings-publish.xml -N \
    org.sonatype.plugins:nexus-staging-maven-plugin:1.6.14:rc-release \
    -DnexusUrl=https://oss.sonatype.org/ \
    -DserverId=ossrh \
    -DstagingProgressTimeoutMinutes=10 \
    -DstagingRepositoryId=$STG_REPO

