#!/usr/bin/ssh-agent /bin/bash 
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail
set -x

if [ $# -ne 2 ]; then
    echo "Usage: $0 <Vespa version> <Git reference>"
    exit 1
fi

readonly VESPA_RELEASE="$1"
readonly VESPA_REF="$2"

# Cloudsmith repo
rpm --import 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/gpg.0F3DA3C70D35DA7B.key'
curl -1sLf 'https://dl.cloudsmith.io/public/vespa/open-source-rpms/config.rpm.txt?distro=el&codename=8' > /tmp/vespa-open-source-rpms.repo
dnf config-manager --add-repo '/tmp/vespa-open-source-rpms.repo'
rm -f /tmp/vespa-open-source-rpms.repo

VESPA_RPM_X86_64=$(dnf repoquery -y --forcearch x86_64 --repoid=vespa-open-source-rpms -q vespa | cut -d: -f2 | cut -d- -f1 | sort -V | tail -1)
echo "Latest x86_64 RPM on Copr: $VESPA_RPM_X86_64"

VESPA_RPM_AARCH64=$(dnf repoquery -y --forcearch aarch64 --repoid=vespa-open-source-rpms -q vespa | cut -d: -f2 | cut -d- -f1 | sort -V | tail -1)
echo "Latest aarch64 RPM on Copr: $VESPA_RPM_AARCH64"

if [[ "$VESPA_RELEASE" == "$VESPA_RPM_X86_64" ]] &&  [[ "$VESPA_RELEASE" == "$VESPA_RPM_AARCH64" ]]; then
  echo "Vespa RPMs for version $VESPA_RELEASE already exists. Exiting."
  exit 0
fi

echo "Using vespa repository git reference: $VESPA_REF"

ssh-add -D
set +x
ssh-add <(echo $VESPA_DEPLOY_TOKEN | base64 -d)
set -x
git clone git@github.com:vespa-engine/vespa

cd vespa
dist/release-vespa-rpm.sh $VESPA_RELEASE $VESPA_REF

upload_rpms() {
  local ARCH=$1
  aws s3 cp  s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/$ARCH/rpm-repo.tar .
  aws s3 cp  s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/$ARCH/rpm-repo.tar.pem .
  aws s3 cp  s3://381492154096-build-artifacts/vespa-engine--vespa/$VESPA_RELEASE/artifacts/$ARCH/rpm-repo.tar.sig .
  cosign verify-blob \
    --certificate-identity https://buildkite.com/vespaai/vespa-engine-vespa \
    --certificate-oidc-issuer https://agent.buildkite.com \
    --signature rpm-repo.tar.sig \
    --certificate rpm-repo.tar.pem \
    rpm-repo.tar
  tar xvf rpm-repo.tar
  for rpm in rpms/*.rpm; do
    screwdriver/upload-rpm-to-cloudsmith.sh $rpm
  done
  rm -rf rpms rpm-repo.*
}

if [[ "$VESPA_RELEASE" != "$VESPA_RPM_X86_64" ]]; then
  upload_rpms amd64
fi

if [[ "$VESPA_RELEASE" != "$VESPA_RPM_AARCH64" ]]; then
  upload_rpms arm64
fi
