#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
dnf install -y 'dnf-command(config-manager)' jq

set -euo pipefail
MAX_NUMBER_OF_RELEASES=40

# Cloudsmith repo
rpm --import 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/gpg.0F3DA3C70D35DA7B.key'
curl -1sLf 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/config.rpm.txt?distro=el&codename=8' > /tmp/vespa-open-source-rpms.repo
dnf config-manager --add-repo '/tmp/vespa-open-source-rpms.repo'
rm -f /tmp/vespa-open-source-rpms.repo

# Allow the last Vespa 7 release to remain in the repo
VERSIONS_TO_DELETE=$(dnf list -y --quiet --showduplicates --disablerepo='*' --enablerepo=vespa-open-source-rpms vespa | awk '/[0-9].*\.[0-9].*\.[0-9].*/{print $2}' | sort -V | grep -v "7.594.36" | head -n -$MAX_NUMBER_OF_RELEASES)

if [[ -z "$VERSIONS_TO_DELETE" ]]; then
  echo "No old RPM versions to delete found. Exiting."
  exit 0
fi

RPMS_TO_DELETE=$(mktemp)
trap "rm -f $RPMS_TO_DELETE" EXIT

for VERSION in $VERSIONS_TO_DELETE; do
  curl -sLf --header 'accept: application/json' \
    "https://api.cloudsmith.io/v1/packages/vespa/open-source-rpms/?query=version:${VERSION}" | jq -re '.[] | .slug' >> $RPMS_TO_DELETE
done

echo "Deleting the following RPMs:"
cat $RPMS_TO_DELETE

if [ "$GITHUB_EVENT_NAME" == "schedule" ]; then
    for RPMID in $(cat $RPMS_TO_DELETE); do
      curl -sLf -X DELETE \
        --header "X-Api-Key: $CLOUDSMITH_API_TOKEN" \
        --header 'accept: application/json' \
        "https://api.cloudsmith.io/v1/packages/vespa/open-source-rpms/$RPMID/"
    done
fi


