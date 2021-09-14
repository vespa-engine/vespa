#!/bin/bash

set -euo pipefail

MAX_NUMBER_OF_RELEASES=200
ARTIFACTORY_URL="https://artifactory.verizonmedia.com/artifactory"

# JFrog Cloud repo file
if [[ ! -f /etc/yum.repos.d/vespa.repo ]]; then
    cat << EOF > /etc/yum.repos.d/vespa.repo
[vespa-release]
name=Vespa releases
baseurl=$ARTIFACTORY_URL/vespa/centos/7/release/\$basearch
gpgcheck=0
enabled=1
EOF
fi

VERSIONS_TO_DELETE=$(yum list --quiet --showduplicates --disablerepo='*' --enablerepo=vespa-release vespa | awk '/[0-9].*\.[0-9].*\.[0-9].*/{print $2}' | sort -V | head -n -200)

RPMS_TO_DELETE=$(mktemp)
trap "rm -f $RPMS_TO_DELETE" EXIT

for VERSION in $VERSIONS_TO_DELETE; do
    curl -sSL -H "content-type:text/plain" -H "Authorization: Bearer $JFROG_API_TOKEN" \
         --data "items.find({ \"repo\": { \"\$eq\": \"vespa\" }, \"name\": {\"\$match\": \"vespa*$VERSION*\"}   }).include(\"repo\", \"path\", \"name\")" \
         "$ARTIFACTORY_URL/api/search/aql" \
        | jq -re ".results[]|\"$ARTIFACTORY_URL/\(.repo)/\(.path)/\(.name)\"" >> $RPMS_TO_DELETE
done

echo "Deleting the following RPMs:"
cat $RPMS_TO_DELETE

if [[ -n $SCREWDRIVER ]] && [[ -z $SD_PULL_REQUEST ]]; then
    for RPM in $(cat $RPMS_TO_DELETE); do
        curl -sSL -H "Authorization: Bearer $JFROG_API_TOKEN" -X DELETE $RPM
    done
fi


