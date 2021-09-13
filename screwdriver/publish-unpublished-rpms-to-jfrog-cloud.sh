#!/bin/bash

set -euo pipefail

readonly MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

yum install -y yum-utils &> /dev/null

# Copr repo file
if [[ ! -f /etc/yum.repos.d/group_vespa-vespa-epel-7.repo ]]; then
  cat << 'EOF' > /etc/yum.repos.d/group_vespa-vespa-epel-7.repo
[copr:copr.fedorainfracloud.org:group_vespa:vespa]
name=Copr repo for vespa owned by @vespa
baseurl=https://copr-be.cloud.fedoraproject.org/results/@vespa/vespa/epel-7-$basearch/
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
baseurl=https://artifactory.verizonmedia.com/artifactory/vespa/centos/7/release/$basearch
gpgcheck=0
enabled=1
EOF
fi

readonly COPR_PACKAGES=$(mktemp)
trap "rm -f $COPR_PACKAGES" EXIT

yum list -q --disablerepo='*' --enablerepo=copr:copr.fedorainfracloud.org:group_vespa:vespa --showduplicates 'vespa*' | grep "Available Packages" -A 100000 | tail -n +2 | sed "s/\.x86_64\ */-/"| awk '{print $1}' > $COPR_PACKAGES

echo "Packages on Copr:"
cat $COPR_PACKAGES
echo 

for pv in $(cat $COPR_PACKAGES); do
  if ! yum list -q --disablerepo='*' --enablerepo=vespa-release $pv &> /dev/null; then
    echo "$pv not found on JFrog Clould. Downloading..."
    yumdownloader -q $pv
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

if [[ -n $SCREWDRIVER ]] && [[ -z $SD_PULL_REQUEST ]]; then
  for rpm in $(ls *.rpm); do
    echo "Uploading $rpm ..."
    if ! $MYDIR/upload-rpm-to-artifactory.sh $rpm ; then
      echo "Could not upload $rpm"
    else
      echo "$rpm uploaded"
    fi
  done
  echo
fi

