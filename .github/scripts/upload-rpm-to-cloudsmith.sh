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

if (( $# < 2 )); then
  echo "Usage: $0 <RPM file> <OS version>"
  exit 1
fi

: "${CLOUDSMITH_API_TOKEN:?Environment variable CLOUDSMITH_API_TOKEN must be set.}"

main() {
  local RPM=$1 ; shift
  local OS_VERSION=$2 ; shift
  local OS_DISTRO=el # Assuming RHEL/CentOS/Alma/Rocky.

  FID=$(curl -sSLf \
    --upload-file "$RPM" \
    -H "X-Api-Key: ${CLOUDSMITH_API_TOKEN}" \
    -H "Content-Sha256: $(sha256sum "$RPM" | cut -f1 -d' ')" \
    "https://upload.cloudsmith.io/vespa/open-source-rpms/$RPM" | jq -re '.identifier')

  if [[ -n $FID ]]; then
    curl -sSLf -X POST -H "Content-Type: application/json" \
      -H "X-Api-Key: ${CLOUDSMITH_API_TOKEN}" \
      -d "{\"package_file\": \"$FID\", \"distribution\": \"$OS_DISTRO/$OS_VERSION\"}" \
      https://api-prd.cloudsmith.io/v1/packages/vespa/open-source-rpms/upload/rpm/
  fi
}

main "$@"
