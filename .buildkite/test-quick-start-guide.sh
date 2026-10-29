#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This test will test the quick start guide that use the vespaengine/vespa:latest image. To test a locally
# generated image, make sure that the image is tagged as vespaengine/vespa:latest.
set -xeuo pipefail

echo "--- 📖 Testing Vespa quick start guide"
TESTDIR=$(mktemp -d)
trap 'rm -rf -- "$TESTDIR"' EXIT

cd "$TESTDIR"

echo "Setting up documentation test environment..."
# Clone and setup doc tests
for attempt in 1 2 3; do
  if timeout --kill-after=10s 300s git clone -q --depth 1 \
      https://github.com/vespa-engine/documentation "$TESTDIR/documentation"; then
    break
  else
    status=$?
  fi
  # A failed clone may leave a partial checkout behind.
  rm -rf -- "$TESTDIR/documentation"
  if [[ "$attempt" == 3 ]]; then
    exit "$status"
  fi
  echo "Documentation clone failed (attempt $attempt/3); retrying in 2 seconds" >&2
  sleep 2
done
cd documentation

testfile="en/basics/deploy-an-application-local.html"
if [[ ! -f "$testfile" ]]; then
  echo "ERROR: The quick start test file is missing: ${testfile}" 1>&2
  echo "ERROR: Expected to be found on https://github.com/vespa-engine/documentation/tree/master/${testfile}" 1>&2
  exit 1
fi

python3 -m pip install -qqq -r test/requirements.txt --user
echo -e "urls:\n    - ${testfile}" > test/_deploy-an-application-local.yaml

echo "Downloading Vespa CLI..."
# Get the required vespa CLI
download() {
  curl -fsSL --connect-timeout 20 --max-time 120 \
      --retry 2 --retry-delay 2 --retry-max-time 300 --output "$2" "$1"
}
download https://api.github.com/repos/vespa-engine/vespa/releases/latest "$TESTDIR/release.json"
VESPA_CLI_VERSION=$(grep -Po '"tag_name": "v\K.*?(?=")' "$TESTDIR/release.json")
if [[ $(arch) == x86_64 ]]; then
  GO_ARCH=amd64
else
  GO_ARCH=arm64
fi
download "https://github.com/vespa-engine/vespa/releases/download/v${VESPA_CLI_VERSION}/vespa-cli_${VESPA_CLI_VERSION}_linux_${GO_ARCH}.tar.gz" "$TESTDIR/vespa-cli.tar.gz"
tar -zxf "$TESTDIR/vespa-cli.tar.gz" -C /opt
ln -sf "/opt/vespa-cli_${VESPA_CLI_VERSION}_linux_${GO_ARCH}/bin/vespa" /usr/local/bin/

echo "Running deploy an application locally test..."
# Run test
python3 test/test.py -v -c test/_deploy-an-application-local.yaml
