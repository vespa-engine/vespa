#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${RUNNER_DEBUG:-}" ]]; then
    set -o xtrace
fi

if (( $# < 1 )); then
  echo "Usage: $0 <RPM file>"
  exit 1
fi

if [ "${CLOUDSMITH_API_TOKEN}" = "" ]; then
  echo "Environment CLOUDSMITH_API_TOKEN not set. Exiting."
  exit 1
fi

RPM=$1
OS_DISTRO=el
RELEASEVER=8

main() {

  FID=$(curl -sSLf \
    --upload-file "$RPM" \
    -H "X-Api-Key: ${CLOUDSMITH_API_TOKEN}" \
    -H "Content-Sha256: $(sha256sum "$RPM" | cut -f1 -d' ')" \
    "https://upload.cloudsmith.io/vespa/open-source-rpms/$RPM" | jq -re '.identifier')

  if [[ -n $FID ]]; then
    curl -sSLf -X POST -H "Content-Type: application/json" \
      -H "X-Api-Key: ${CLOUDSMITH_API_TOKEN}" \
      -d "{\"package_file\": \"$FID\", \"distribution\": \"$OS_DISTRO/$RELEASEVER\"}" \
      https://api-prd.cloudsmith.io/v1/packages/vespa/open-source-rpms/upload/rpm/
  fi
}

main "$@"
