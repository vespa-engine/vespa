#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

if (( $# < 1 )); then
    echo "Usage: $0 <RPM architecture>"
    exit 1
fi

RPMARCH=$1
ALLOWED_ARCHS=("x86_64" "aarch64")

if [[ ! ${ALLOWED_ARCHS[@]} =~ $RPMARCH ]]; then
  echo "Architecture $RPMARCH not in allowed archs: ${ALLOWED_ARCHS[@]}"
  exit 1
fi

readonly MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Copr repo
dnf config-manager --add-repo https://copr.fedorainfracloud.org/coprs/g/vespa/vespa/repo/epel-8/group_vespa-vespa-epel-8.repo
sed -i "s,\$basearch,$RPMARCH,g" /etc/yum.repos.d/group_vespa-vespa-epel-8.repo

# Cloudsmith repo
rpm --import 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/gpg.0F3DA3C70D35DA7B.key'
curl -1sLf 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/config.rpm.txt?distro=el&codename=8' > /tmp/vespa-open-source-rpms.repo
dnf config-manager --add-repo '/tmp/vespa-open-source-rpms.repo'
rm -f /tmp/vespa-open-source-rpms.repo

readonly COPR_PACKAGES=$(mktemp)
trap "rm -f $COPR_PACKAGES" EXIT
readonly DLDIR=$(mktemp -d)
trap "rm -rf $DLDIR" EXIT

cd $DLDIR

readonly DNF="dnf -y -q --forcearch $RPMARCH"

$DNF list --disablerepo='*' --enablerepo=copr:copr.fedorainfracloud.org:group_vespa:vespa --showduplicates 'vespa*' | grep "Available Packages" -A 100000 | tail -n +2 | sed '/\.src\ */d' | sed -E "s/\.($RPMARCH|noarch)\ */-/" | awk '{print $1}' | grep -v '.src$' > $COPR_PACKAGES

echo "Packages on Copr:"
cat $COPR_PACKAGES
echo 

for pv in $(cat $COPR_PACKAGES); do
  if ! $DNF list --disablerepo='*' --enablerepo=vespa-open-source-rpms $pv &> /dev/null; then
    # Need one extra check here for noarch packages
    if ! dnf -y -q --forcearch noarch list --disablerepo='*' --enablerepo=vespa-open-source-rpms $pv &> /dev/null; then
      echo "$pv not found on in archive. Downloading..."
      $DNF download --disablerepo='*' --enablerepo=copr:copr.fedorainfracloud.org:group_vespa:vespa $pv
      echo "$pv downloaded."
    fi
  fi
done
echo

if ! ls *.rpm &> /dev/null; then
  echo "All packages already in archive."
  exit 0
fi

echo "RPMs missing in archive:"
ls -lh  *.rpm
echo

UPLOAD_FAILED=false
if [[ -n $SCREWDRIVER ]] && [[ -z $SD_PULL_REQUEST ]]; then
  for rpm in $(ls *.rpm); do
    echo "Uploading $rpm ..."
    if ! $MYDIR/upload-rpm-to-cloudsmith.sh $rpm ; then
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
