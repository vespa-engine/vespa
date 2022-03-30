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

QUERY_VERSION_HTTP_CODE=$(curl --write-out %{http_code} --silent --location --output /dev/null https://oss.sonatype.org/content/repositories/releases/com/yahoo/vespa/parent/${VESPA_RELEASE}/)
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
openssl aes-256-cbc -md md5 -pass pass:$GPG_ENCPHRASE -in $SD_SOURCE_DIR/screwdriver/pubring.gpg.enc -out $SD_SOURCE_DIR/screwdriver/deploy/pubring.gpg -d
openssl aes-256-cbc -md md5 -pass pass:$GPG_ENCPHRASE -in $SD_SOURCE_DIR/screwdriver/secring.gpg.enc -out $SD_SOURCE_DIR/screwdriver/deploy/secring.gpg -d

# Build the Java code with the correct version set
find . -name "pom.xml" -exec sed -i'' -e "s,<version>.*SNAPSHOT.*</version>,<version>$VESPA_RELEASE</version>," \
     -e "s,<vespaversion>.*project.version.*</vespaversion>,<vespaversion>$VESPA_RELEASE</vespaversion>," \
     -e "s,<test-framework.version>.*project.version.*</test-framework.version>,<test-framework.version>$VESPA_RELEASE</test-framework.version>," \
     {} \;

# We disable javadoc for all modules not marked as public API
for MODULE in $(comm -2 -3 \
                <(find . -name "*.java" | awk -F/ '{print $2}' | sort -u)
                <(find . -name "package-info.java" -exec grep -HnE "@(com.yahoo.api.annotations.)?PublicApi.*" {} \; | awk -F/ '{print $2}' | sort -u)); do
    mkdir -p $MODULE/src/main/javadoc
    echo "No javadoc available for module" > $MODULE/src/main/javadoc/README
done

./bootstrap.sh

COMMON_MAVEN_OPTS="--batch-mode --no-snapshot-updates --settings $(pwd)/screwdriver/settings-publish.xml --activate-profiles ossrh-deploy-vespa -DskipTests"
TMPFILE=$(mktemp)
mvn $COMMON_MAVEN_OPTS  -pl :container-dependency-versions -DskipStagingRepositoryClose=true deploy 2>&1 | tee $TMPFILE

# Find the stage repo name
STG_REPO=$(cat $TMPFILE | grep 'Staging repository at http' | head -1 | awk -F/ '{print $NF}')
rm -f $TMPFILE

# Deploy plugins
mvn $COMMON_MAVEN_OPTS --file ./maven-plugins/pom.xml -DskipStagingRepositoryClose=true -DstagingRepositoryId=$STG_REPO deploy

# Deploy the rest of the artifacts
mvn $COMMON_MAVEN_OPTS --threads 1C -DskipStagingRepositoryClose=true -DstagingRepositoryId=$STG_REPO deploy

# Close with checks
mvn $COMMON_MAVEN_OPTS -N org.sonatype.plugins:nexus-staging-maven-plugin:1.6.8:rc-close -DnexusUrl=https://oss.sonatype.org/ -DserverId=ossrh -DstagingRepositoryId=$STG_REPO

# Release if ok
mvn $COMMON_MAVEN_OPTS -N org.sonatype.plugins:nexus-staging-maven-plugin:1.6.8:rc-release -DnexusUrl=https://oss.sonatype.org/ -DserverId=ossrh -DstagingRepositoryId=$STG_REPO

# Delete the GPG rings
rm -rf $SD_SOURCE_DIR/screwdriver/deploy

