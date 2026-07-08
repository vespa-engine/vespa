#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Sets up Vespa and runs basic search system tests.

set -o errexit
set -o nounset
set -o pipefail

if [[ -n "${DEBUG:-}" ]]; then
    set -o xtrace
fi

: "${VESPA_VERSION:?Environment variable VESPA_VERSION must be set (version to build)}"
: "${LOCAL_RPM_REPO:?Environment variable LOCAL_RPM_REPO must be set (path to local RPM repo)}"

echo "--- 🔧 Setting up Vespa RPM repository"
echo -e "[vespa-rpms-local]\nname=Local Vespa RPMs\nbaseurl=file://${LOCAL_RPM_REPO}\nenabled=1\ngpgcheck=0" > /etc/yum.repos.d/vespa-rpms-local.repo

echo "Installing Vespa $VESPA_VERSION..."
if ! rpm -q "vespa-$VESPA_VERSION"; then
    dnf -y install "vespa-$VESPA_VERSION"
else
    dnf -y reinstall "vespa-$VESPA_VERSION"
fi

echo "--- 🧪 Setting up system test environment"
SYSTEM_TEST_DIR=$WORKDIR/system-test
if [[ ! -d $SYSTEM_TEST_DIR ]]; then
    echo "Cloning system-test repository..."
    git clone --quiet --depth 1 https://github.com/vespa-engine/system-test "$SYSTEM_TEST_DIR"
else
    echo "Using existing system-test repository"
fi

export RUBYLIB="$SYSTEM_TEST_DIR/lib:$SYSTEM_TEST_DIR/tests"

echo "Preparing test environment..."
# Workaround for /opt/vespa/tmp directory created by systemtest runner
mkdir -p /opt/vespa/tmp
chmod 1777 /opt/vespa/tmp

echo "--- 🚀 Running basic search test"
export USER=vespa
"$SYSTEM_TEST_DIR/lib/node_server.rb" &
NODE_SERVER_PID=$!
# shellcheck disable=2064
trap "kill $NODE_SERVER_PID" EXIT

sleep 3
echo "Executing basic search test..."
ruby "$SYSTEM_TEST_DIR/tests/search/basicsearch/basic_search.rb" || (/opt/vespa/bin/vespa-logfmt -N && false)
