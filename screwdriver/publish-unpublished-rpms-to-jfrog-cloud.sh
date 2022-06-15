#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

readonly MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Copr repo file
if [[ ! -f /etc/yum.repos.d/group_vespa-vespa-epel-7.repo ]]; then
  cat << 'EOF' > /etc/yum.repos.d/group_vespa-vespa-epel-7.repo
[copr:copr.fedorainfracloud.org:group_vespa:vespa]
name=Copr repo for vespa owned by @vespa
baseurl=https://download.copr.fedorainfracloud.org/results/@vespa/vespa/centos-stream-8-$basearch/
type=rpm-md
gpgcheck=1
gpgkey=https://copr-be.cloud.fedoraproject.org/results/@vespa/vespa/pubkey.gpg
repo_gpgcheck=0
enabled=1
EOF
fi

# JFrog Cloud repo file
if [[ ! -f /etc/yum.repos.d/vespa.repo ]]; then
  cat << 'EOF' > /etc/yum.repos.d/vespa.repo
[vespa-release]
name=Vespa releases
baseurl=https://artifactory.yahooinc.com/artifactory/vespa/centos/8/release/$basearch
gpgcheck=0
enabled=1
EOF
fi

readonly COPR_PACKAGES=$(mktemp)
trap "rm -f $COPR_PACKAGES" EXIT

dnf list -q --disablerepo='*' --enablerepo=copr:copr.fedorainfracloud.org:group_vespa:vespa --showduplicates 'vespa*' | grep "Available Packages" -A 100000 | tail -n +2 | sed '/\.src\ */d' | sed 's/\.x86_64\ */-/' | awk '{print $1}' | grep -v '.src$' > $COPR_PACKAGES

echo "Packages on Copr:"
cat $COPR_PACKAGES
echo 

for pv in $(cat $COPR_PACKAGES); do
  if ! dnf list -q --disablerepo='*' --enablerepo=vespa-release $pv &> /dev/null; then
    echo "$pv not found on JFrog Clould. Downloading..."
    dnf download -q --disablerepo='*' --enablerepo=copr:copr.fedorainfracloud.org:group_vespa:vespa $pv
    echo "$pv downloaded."
  fi
done
echo

if ! ls *.rpm &> /dev/null; then
  echo "All packages already on JFrog Cloud."
  exit 0
fi

echo "RPMs missing on JFrog Cloud:"
ls -lh  *.rpm
echo

UPLOAD_FAILED=false
if [[ -n $SCREWDRIVER ]] && [[ -z $SD_PULL_REQUEST ]]; then
  for rpm in $(ls *.rpm); do
    echo "Uploading $rpm ..."
    if ! $MYDIR/upload-rpm-to-artifactory.sh $rpm ; then
      echo "Could not upload $rpm"
      UPLOAD_FAILED=true
    else
      echo "$rpm uploaded"
    fi
  done
  echo
fi

if $UPLOAD_FAILED; then
  echo "Some RPMs failed to upload"
  exit 1
fi
