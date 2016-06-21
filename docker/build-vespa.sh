#!/bin/bash
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <vespa version>"
fi
VESPA_VERSION=$1

docker build -t vespabuild -f Dockerfile.build .
docker run --rm -v $(pwd)/..:/vespa --entrypoint /vespa/docker/build-vespa-internal.sh vespabuild "$VESPA_VERSION"

