#!/usr/bin/ssh-agent /bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

export VESPA_RELEASE="$1"
export JAVA_HOME=$(dirname $(dirname $(readlink -f /usr/bin/java)))

BUILD_DIR=$(mktemp -d)
trap "rm -rf $BUILD_DIR" EXIT
cd $BUILD_DIR

function is_published {
    local TMP_MVN_REPO=$BUILD_DIR/maven-repo
    echo $TMP_MVN_REPO
    mkdir -p $TMP_MVN_REPO
    rm -rf $TMP_MVN_REPO/com/yahoo/vespa
    # Because the transfer of artifacts to Maven Central is not atomic we can't just check a simple pom or jar to be available. Because of this we
    # check that the publication is complete enough to compile a Java sample app

    # Update Vespa version property in pom.xml
    if ! mvn -V -B versions:set-property -Dproperty=vespa_version -DnewVersion=${VESPA_RELEASE}; then
        return 1
    fi
    if mvn -V -B -pl ai.vespa.examples:album-recommendation-java -Dmaven.repo.local=$TMP_MVN_REPO -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -DskipTests clean package; then
        return 0
    else
        return 1
    fi
}

function wait_until_published {
    cnt=0
    until is_published; do
        ((cnt+=1))
        # Wait max 60 minutes
        if (( $cnt > 60 )); then
            echo "ERROR: Artifacts with version ${VESPA_RELEASE} not found on central maven repo."
            exit 1
        fi
        echo "Waiting 60 seconds for version ${VESPA_RELEASE} on central maven repo ($cnt times)."
        sleep 60
    done
}

ssh-add -D
set +x
ssh-add <(echo $SAMPLE_APPS_DEPLOY_TOKEN | base64 -d)
set -x

git clone git@github.com:vespa-engine/sample-apps.git
cd sample-apps

wait_until_published

changes=$(git status --porcelain | wc -l)

if (( changes > 0 )); then
    echo "Updating Vespa version to ${VESPA_RELEASE}."
    git commit -a -m "Update Vespa version to ${VESPA_RELEASE}."
    git pull --rebase
    git push
fi
