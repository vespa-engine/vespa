#!/bin/bash
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <vespa version>"
  exit 1
fi
VESPA_VERSION=$1

docker build -t vespabuild -f Dockerfile.build .

TMP=$(mktemp -d)
docker run --rm -v $(pwd)/..:/vespa -v $TMP:/root/rpmbuild --entrypoint /vespa/docker/build-vespa-internal.sh vespabuild "$VESPA_VERSION"

rm -rf $TMP

