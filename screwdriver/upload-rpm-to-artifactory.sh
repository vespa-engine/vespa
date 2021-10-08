#!/bin/bash

set -euo pipefail

RPM=$1
OS_DISTRO=centos
RELEASEVER=7
MATURITY=release
BASEARCH=x86_64

main() {
  if [[ -z $JFROG_API_TOKEN ]] || [[ -z $RPM ]]; then
      echo "Usage: $0 <RPM package>."
      echo "Environment variable JFROG_API_TOKEN must be set in environment."
      exit 1
  fi

  curl -vL -H "Authorization: Bearer $JFROG_API_TOKEN" \
           -H "X-Checksum-Sha1: $(sha1sum $RPM | awk '{print $1}')" \
           -H "X-Checksum-Sha256: $(sha256sum $RPM | awk '{print $1}')" \
           -H "X-Checksum-MD5: $(md5sum $RPM | awk '{print $1}')" \
           -X PUT "https://artifactory.verizonmedia.com/artifactory/vespa/$OS_DISTRO/$RELEASEVER/$MATURITY/$BASEARCH/Packages/$RPM" \
           -T $RPM
}

main "$@"
