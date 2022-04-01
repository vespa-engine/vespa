#!/usr/bin/ssh-agent /bin/bash 
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

if [ $# -ne 2 ]; then
    echo "Usage: $0 <Vespa version> <Git reference>"
    exit 1
fi

readonly VESPA_RELEASE="$1"
readonly VESPA_REF="$2"

VESPA_RPM=$(dnf repoquery --repofrompath=vespa,https://copr-be.cloud.fedoraproject.org/results/@vespa/vespa/epel-7-x86_64 --repoid=vespa -q vespa | tail -1 | cut -d: -f2 | cut -d- -f1)
echo "Latest RPM on Copr: $VESPA_RPM"

if [ "$VESPA_RELEASE" == "$VESPA_RPM" ]; then
  echo "Vespa rpm for version $VESPA_RELEASE already exists. Exiting."
  exit 0
fi

echo "Using vespa repository git reference: $VESPA_REF"

ssh-add -D
set +x
ssh-add <(echo $VESPA_DEPLOY_KEY | base64 -d)
set -x
git clone git@github.com:vespa-engine/vespa

cd vespa
dist/release-vespa-rpm.sh $VESPA_RELEASE $VESPA_REF

while [ "$VESPA_RELEASE" != "$VESPA_RPM" ]; do
  dnf clean --repofrompath=vespa,https://copr-be.cloud.fedoraproject.org/results/@vespa/vespa/epel-7-x86_64 --repoid=vespa metadata
  VESPA_RPM=$(dnf repoquery --repofrompath=vespa,https://copr-be.cloud.fedoraproject.org/results/@vespa/vespa/epel-7-x86_64 --repoid=vespa -q vespa | tail -1 | cut -d: -f2 | cut -d- -f1)
  echo "RPM: $VESPA_RPM"
  sleep 150
done
