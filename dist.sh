#!/bin/sh
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ -z "$1" ]; then
  echo "Usage: $0 VERSION" 2>&1
  exit 1
fi

VERSION="$1"

mkdir -p ~/rpmbuild/{SOURCES,SPECS}
git archive --format=tar.gz --prefix=vespa-$VERSION/ -o ~/rpmbuild/SOURCES/vespa-$VERSION.tar.gz HEAD

DIST_FILE="dist/vespa.spec"
# When checking out relase tags, the vespa.spec is in the source root folder. This is a workaround to be able to build rpms from a release tag.
if [ ! -e "$DIST_FILE" ]; then
  DIST_FILE="vespa.spec"
fi

sed -e "s,_VESPA_VERSION_,$VERSION,"  < "$DIST_FILE" > ~/rpmbuild/SPECS/vespa-$VERSION.spec
