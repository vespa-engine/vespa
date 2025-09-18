#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Generates a container tag name based on the provided arguments.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

if [[ $# -ne 3 ]]; then
    echo "Usage: $0 <registry> <image_name> <vespa_version>"
    exit 1
fi

os-suffix() {
  # Set default tag to be pointing to Almalinux 8 version.
  # TODO: Update when we switch default OS
  if [[ $ALMALINUX_MAJOR != "8" ]]; then
      echo "-al${ALMALINUX_MAJOR}"
  fi
}

main() {
  local registry="$1" ; shift
  local image_name="$1" ; shift
  local vespa_version="$1" ; shift

  echo "${registry}/${image_name}:${vespa_version}$(os-suffix)"
}

main "$@"
