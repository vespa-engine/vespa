#!/usr/bin/ssh-agent /bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <Vespa version>"
    exit 1
fi

readonly VESPA_RELEASE="$1"
readonly TRUE="true"
readonly FALSE="false"

export JAVA_HOME=$(dirname $(dirname $(readlink -f /usr/bin/java)))

function is_published {
    BUNDLE_PLUGIN_HTTP_CODE=$(curl --write-out %{http_code} --silent --location --output /dev/null https://repo.maven.apache.org/maven2/com/yahoo/vespa/bundle-plugin/${VESPA_RELEASE}/)
    if [ $BUNDLE_PLUGIN_HTTP_CODE = "200" ] ; then
        echo "$TRUE"
    else
        echo "$FALSE"
    fi
}

function wait_until_published {
    cnt=0
    until [[ $(is_published) = "$TRUE" ]]; do
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

wait_until_published

ssh-add -D
set +x
ssh-add <(echo $SAMPLE_APPS_DEPLOY_KEY | base64 -d)
set -x

git clone git@github.com:vespa-engine/sample-apps.git
cd sample-apps

# Update Vespa version property in pom.xml
mvn versions:set-property -Dproperty=vespa_version -DnewVersion=${VESPA_RELEASE}

changes=$(git status --porcelain | wc -l)

if (( changes > 0 )); then
    echo "Updating Vespa version to ${VESPA_RELEASE}."
    git commit -a -m "Update Vespa version to ${VESPA_RELEASE}."
    git pull --rebase
    git push
fi
