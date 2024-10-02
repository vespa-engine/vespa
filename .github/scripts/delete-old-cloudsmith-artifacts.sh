#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ "${RUNNER_DEBUG:-}" == "1" ]; then
    set -o xtrace
fi

set -o errexit
set -o nounset
set -o pipefail

dnf install -y 'dnf-command(config-manager)' jq

MAX_NUMBER_OF_RELEASES=32
LAST_VESPA_7_RELEASE="7.594.36"

# Cloudsmith repo
rpm --import 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/gpg.0F3DA3C70D35DA7B.key'
curl  --silent --show-error --location --fail \
  --tlsv1 --output /tmp/vespa-open-source-rpms.repo \
  'https://dl.cloudsmith.io/public/vespa/open-source-rpms/config.rpm.txt?distro=el&codename=8'
dnf config-manager --add-repo '/tmp/vespa-open-source-rpms.repo'
rm -f /tmp/vespa-open-source-rpms.repo

VESPA_VERSIONS=$(dnf list -y --quiet --showduplicates --disablerepo='*' --enablerepo=vespa-open-source-rpms vespa || true)
if [[ -z "$VESPA_VERSIONS" ]]; then
  echo "No Vespa versions found, nothing to do. Exiting."
  exit 0
fi

# Note: Allow the last Vespa 7 release to remain in the repo!
VERSIONS_TO_DELETE=$(echo "${VESPA_VERSIONS}" | awk '/[0-9].*\.[0-9].*\.[0-9].*/{print $2}' | sort -V | grep -v "${LAST_VESPA_7_RELEASE}" | head -n -$MAX_NUMBER_OF_RELEASES)
if [[ -z "$VERSIONS_TO_DELETE" ]]; then
  echo "No old RPM versions to delete found. Exiting."
  exit 0
fi

RPMS_TO_DELETE=$(mktemp)
trap "rm -f $RPMS_TO_DELETE" EXIT

for VERSION in $VERSIONS_TO_DELETE; do
  curl --silent --show-error --location --fail \
    --header 'accept: application/json' \
    "https://api.cloudsmith.io/v1/packages/vespa/open-source-rpms/?query=version:${VERSION}" | jq -re '.[] | .slug' >> $RPMS_TO_DELETE
done

echo "Deleting the following RPMs:"
cat $RPMS_TO_DELETE

if [[ "$GITHUB_EVENT_NAME" == "schedule" || "$GITHUB_EVENT_NAME" == "workflow_dispatch" ]]; then
    for RPMID in $(cat $RPMS_TO_DELETE); do
      curl --silent --show-error --location --fail \
        --request DELETE \
        --header "X-Api-Key: $CLOUDSMITH_API_TOKEN" \
        --header 'accept: application/json' \
        "https://api.cloudsmith.io/v1/packages/vespa/open-source-rpms/$RPMID/"
    done
fi
