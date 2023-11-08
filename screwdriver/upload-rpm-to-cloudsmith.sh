#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if (( $# < 1 )); then
  echo "Usage: $0 <RPM file>"
  exit 1
fi

if [[ -z $CLOUDSMITH_API_CREDS ]]; then
  echo "Environment CLOUDSMITH_API_CREDS not set. Exiting."
  exit 1
fi

RPM=$1
OS_DISTRO=el
RELEASEVER=8

main() {

  FID=$(curl -sLf \
    --upload-file $RPM \
    -u "$CLOUDSMITH_API_CREDS" \
    -H "Content-Sha256: $(sha256sum $RPM | cut -f1 -d' ')" \
    https://upload.cloudsmith.io/vespa/open-source-rpms/$RPM | jq -re '.identifier')

  if [[ -n $FID ]]; then
    curl -sLf -X POST -H "Content-Type: application/json" \
      -u "$CLOUDSMITH_API_CREDS" \
      -d "{\"package_file\": \"$FID\", \"distribution\": \"$OS_DISTRO/$RELEASEVER\"}" \
      https://api-prd.cloudsmith.io/v1/packages/vespa/open-source-rpms/upload/rpm/
  fi
}

main "$@"
