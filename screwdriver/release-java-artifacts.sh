#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <Vespa version> <Git reference>"
    exit 1
fi

if [[ -z $OSSRH_USER ]] || [[ -z $OSSRH_TOKEN ]]  || [[ -z $GPG_KEYNAME ]] || [[ -z $GPG_PASSPHRASE ]] || [[ -z $GPG_ENCPHRASE ]]; then
    echo -e "The follwing env variables must be set:\n OSSRH_USER\n OSSRH_TOKEN\n GPG_KEYNAME\n GPG_PASSPHRASE\n GPG_ENCPHRASE"
    exit 1
fi

readonly VESPA_RELEASE="$1"
readonly VESPA_REF="$2"

QUERY_VERSION_HTTP_CODE=$(curl --write-out %{http_code} --silent --location --output /dev/null https://oss.sonatype.org/content/repositories/releases/com/yahoo/vespa/parent/${VESPA_RELEASE}/parent-${VESPA_RELEASE}.pom)
if [[ "200" == $QUERY_VERSION_HTTP_CODE ]]; then
  echo "Vespa version $VESPA_RELEASE is already promoted, exiting"
  exit 0
fi

export JAVA_HOME=$(dirname $(dirname $(readlink -f /usr/bin/java)))

VESPA_DIR=vespa-clean
git clone https://github.com/vespa-engine/vespa.git $VESPA_DIR

cd $VESPA_DIR
git checkout $VESPA_REF

mkdir -p $SD_SOURCE_DIR/screwdriver/deploy
# gpg-agent in RHEL 8 runs out of memory if we use Maven and sign in parallel. Add option to overcome this.
echo "auto-expand-secmem" >> $SD_SOURCE_DIR/screwdriver/deploy/gpg-agent.conf
openssl aes-256-cbc -md md5 -pass pass:$GPG_ENCPHRASE -in $SD_SOURCE_DIR/screwdriver/pubring.gpg.enc -out $SD_SOURCE_DIR/screwdriver/deploy/pubring.gpg -d
openssl aes-256-cbc -md md5 -pass pass:$GPG_ENCPHRASE -in $SD_SOURCE_DIR/screwdriver/secring.gpg.enc -out $SD_SOURCE_DIR/screwdriver/deploy/secring.gpg -d
chmod 700 $SD_SOURCE_DIR/screwdriver/deploy
chmod 600 $SD_SOURCE_DIR/screwdriver/deploy/*

# Build the Java code with the correct version set
$SD_SOURCE_DIR/screwdriver/replace-vespa-version-in-poms.sh $VESPA_RELEASE .

# We disable javadoc for all modules not marked as public API
for MODULE in $(comm -2 -3 \
                <(find . -name "*.java" | awk -F/ '{print $2}' | sort -u)
                <(find . -name "package-info.java" -exec grep -HnE "@(com.yahoo.api.annotations.)?PublicApi.*" {} \; | awk -F/ '{print $2}' | sort -u)); do
    mkdir -p $MODULE/src/main/javadoc
    echo "No javadoc available for module" > $MODULE/src/main/javadoc/README
done

# Workaround new module without java code and no javadoc
mkdir -p container-spifly/src/main/javadoc
echo "No javadoc available for module" > container-spifly/src/main/javadoc/README

# Workaround for broken nexus-staging-maven-plugin instead of swapping JDK
export MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.desktop/java.awt.font=ALL-UNNAMED"
export VESPA_MAVEN_EXTRA_OPTS="--show-version --batch-mode"
export FACTORY_VESPA_VERSION=$VESPA_RELEASE
./bootstrap.sh

COMMON_MAVEN_OPTS="$VESPA_MAVEN_EXTRA_OPTS --no-snapshot-updates --settings $(pwd)/screwdriver/settings-publish.xml --activate-profiles ossrh-deploy-vespa -DskipTests"
TMPFILE=$(mktemp)
mvn $COMMON_MAVEN_OPTS  -pl :container-dependency-versions -DskipStagingRepositoryClose=true deploy 2>&1 | tee $TMPFILE

# Find the stage repo name
STG_REPO=$(cat $TMPFILE | grep 'Staging repository at http' | head -1 | awk -F/ '{print $NF}')
rm -f $TMPFILE

# Deploy plugins
mvn $COMMON_MAVEN_OPTS --file ./maven-plugins/pom.xml -DskipStagingRepositoryClose=true -DstagingRepositoryId=$STG_REPO deploy

# Deploy the rest of the artifacts
mvn $COMMON_MAVEN_OPTS --threads 8 -DskipStagingRepositoryClose=true -DstagingRepositoryId=$STG_REPO deploy

# Close with checks
mvn $COMMON_MAVEN_OPTS -N org.sonatype.plugins:nexus-staging-maven-plugin:1.6.12:rc-close -DnexusUrl=https://oss.sonatype.org/ -DserverId=ossrh -DstagingRepositoryId=$STG_REPO

# Release if ok
mvn $COMMON_MAVEN_OPTS -N org.sonatype.plugins:nexus-staging-maven-plugin:1.6.12:rc-release -DnexusUrl=https://oss.sonatype.org/ -DserverId=ossrh -DstagingRepositoryId=$STG_REPO

# Delete the GPG rings
rm -rf $SD_SOURCE_DIR/screwdriver/deploy

