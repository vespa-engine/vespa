#!/usr/bin/ssh-agent /bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

readonly VESPA_RELEASE="$1"
export JAVA_HOME=$(dirname $(dirname $(readlink -f /usr/bin/java)))

function is_published {
    local TMP_MVN_REPO=/tmp/maven-repo
    echo $TMP_MVN_REPO
    mkdir -p $TMP_MVN_REPO
    rm -rf $TMP_MVN_REPO/com/yahoo/vespa
    # Because the transfer of artifacts to Maven Central is not atomic we can't just check a simple pom or jar to be available. Because of this we
    # check that the publication is complete enough to compile a Java sample app
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
ssh-add <(echo $SAMPLE_APPS_DEPLOY_KEY | base64 -d)
set -x

git clone git@github.com:vespa-engine/sample-apps.git
cd sample-apps

# Update Vespa version property in pom.xml
mvn -V -B versions:set-property -Dproperty=vespa_version -DnewVersion=${VESPA_RELEASE}

wait_until_published

changes=$(git status --porcelain | wc -l)

if (( changes > 0 )); then
    echo "Updating Vespa version to ${VESPA_RELEASE}."
    git commit -a -m "Update Vespa version to ${VESPA_RELEASE}."
    git pull --rebase
    git push
fi
