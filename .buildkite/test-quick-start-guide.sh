#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This test will test the quick start guide that use the vespaengine/vespa:latest image. To test a locally
# generated image, make sure that the image is tagged as vespaengine/vespa:latest.
set -xeuo pipefail

TESTDIR=$(mktemp -d)
trap "rm -rf $TESTDIR" EXIT

cd $TESTDIR

# Clone and setup doc tests
git clone -q --depth 1 https://github.com/vespa-engine/documentation
cd documentation
python3 -m pip install -qqq -r test/requirements.txt --user
echo -e "urls:\n    - en/vespa-quick-start.html" > test/_quick-start.yaml

# Get the required vespa CLI
VESPA_CLI_VERSION=$(curl -fsSL https://api.github.com/repos/vespa-engine/vespa/releases/latest | grep -Po '"tag_name": "v\K.*?(?=")')
if [[ $(arch) == x86_64 ]]; then
  GO_ARCH=amd64
else
  GO_ARCH=arm64
fi
curl -fsSL https://github.com/vespa-engine/vespa/releases/download/v${VESPA_CLI_VERSION}/vespa-cli_${VESPA_CLI_VERSION}_linux_${GO_ARCH}.tar.gz | tar -zxf - -C /opt
ln -sf /opt/vespa-cli_${VESPA_CLI_VERSION}_linux_${GO_ARCH}/bin/vespa /usr/local/bin/

# Run test
python3 test/test.py -v -c test/_quick-start.yaml

