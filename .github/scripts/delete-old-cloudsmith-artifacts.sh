#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ "${RUNNER_DEBUG:-}" == "1" ]; then
    set -o xtrace
fi

set -o errexit
set -o nounset
set -o pipefail

dnf install -yq 'dnf-command(config-manager)' jq

MAX_NUMBER_OF_RELEASES=32

# Cloudsmith repo
rpm --import 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/gpg.0F3DA3C70D35DA7B.key'
curl  --silent --show-error --location --fail \
  --tlsv1 --output /tmp/vespa-open-source-rpms.repo \
  'https://dl.cloudsmith.io/public/vespa/open-source-rpms/config.rpm.txt?distro=el&codename=8'
dnf config-manager --add-repo '/tmp/vespa-open-source-rpms.repo'
dnf makecache -y --quiet --repo=vespa-open-source-rpms
rm -f /tmp/vespa-open-source-rpms.repo

dnf list -y --quiet --showduplicates --repo=vespa-open-source-rpms vespa >/tmp/dnf-stdout 2>/tmp/dnf-stderr || true
DNF_STDERR="$(cat /tmp/dnf-stderr)"
DNF_STDOUT="$(cat /tmp/dnf-stdout)"
if [[ "${DNF_STDERR}" == "Error: No matching Packages to list" ]]; then
  echo "No Vespa versions found, nothing to do. Exiting."
  exit 0
elif [[ -n "${DNF_STDERR}" ]]; then
  echo "::error::Unexpected error output from dnf list:"
  echo "${DNF_STDERR}"
  exit 1
elif [[ -z "${DNF_STDOUT}" ]]; then
  echo "::error::No Vespa versions found, but dnf did not return an error. Exiting."
  exit 1
fi

# Note: Only consider 8.x and 9.x releases to preserve the last 7.x release.
VERSIONS_TO_DELETE=$(echo "${DNF_STDOUT}" | awk '/[8-9]+\.[0-9]+\.[0-9]+/{print $2}' | sort -V | head -n -$MAX_NUMBER_OF_RELEASES)
if [[ -z "$VERSIONS_TO_DELETE" ]]; then
  echo "No old RPM versions to delete found. Exiting."
  exit 0
fi

RPMS_TO_DELETE="$(mktemp)"
trap 'rm -f "${RPMS_TO_DELETE}"' EXIT
for VERSION in $VERSIONS_TO_DELETE; do
  curl --silent --show-error --location --fail \
    --header 'accept: application/json' \
    "https://api.cloudsmith.io/v1/packages/vespa/open-source-rpms/?query=version:${VERSION}" | jq -re '.[] | .slug' >> "${RPMS_TO_DELETE}"
done

echo
if [[ "${GITHUB_EVENT_NAME}" == "schedule" || "${GITHUB_EVENT_NAME}" == "workflow_dispatch" ]]; then
    printf '\033[1;33mDeleting the following RPMs:\033[0m\n\n' "${RPMS_TO_DELETE}"

    while IFS= read -r RPMID
    do
      curl --silent --show-error --location --fail \
        --request DELETE \
        --header "X-Api-Key: ${CLOUDSMITH_API_TOKEN}" \
        --header "accept: application/json" \
        "https://api.cloudsmith.io/v1/packages/vespa/open-source-rpms/${RPMID}/"
    done < "${RPMS_TO_DELETE}"
else
    printf '\033[1;32m#### Dry-run mode ####\033[0m\n'
    printf '\033[0;32mWould have deleted the following RPMs:\033[0m\n\n'
    cat "${RPMS_TO_DELETE}"
fi
