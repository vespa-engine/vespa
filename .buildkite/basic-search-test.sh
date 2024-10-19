#!/bin/bash

set -euo pipefail

echo -e "[vespa-rpms-local]\nname=Local Vespa RPMs\nbaseurl=file://$(pwd)/artifacts/$ARCH/rpms\nenabled=1\ngpgcheck=0" > /etc/yum.repos.d/vespa-rpms-local.repo

if ! rpm -q "vespa-$VESPA_VERSION"; then
    dnf -y install "vespa-$VESPA_VERSION"
else
    dnf -y reinstall "vespa-$VESPA_VERSION"
fi

SYSTEM_TEST_DIR=$WORKDIR/system-test
if [[ ! -d $SYSTEM_TEST_DIR ]]; then
    git clone --depth 1 https://github.com/vespa-engine/system-test "$SYSTEM_TEST_DIR"
fi

export RUBYLIB="$SYSTEM_TEST_DIR/lib:$SYSTEM_TEST_DIR/tests"

# Workaround for /opt/vespa/tmp directory created by systemtest runner
mkdir -p /opt/vespa/tmp
chmod 1777 /opt/vespa/tmp

export USER=vespa
"$SYSTEM_TEST_DIR/lib/node_server.rb" &
NODE_SERVER_PID=$!
# shellcheck disable=2064
trap "kill $NODE_SERVER_PID" EXIT

sleep 3
ruby "$SYSTEM_TEST_DIR/tests/search/basicsearch/basic_search.rb" || (/opt/vespa/bin/vespa-logfmt -N && false)


