#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <version> <git ref>"
  exit 1
fi

if [[ -z $COPR_WEBHOOK ]]; then
    echo "This script requires the COPR_WEBHOOK environment variable to be set."
    exit 1
fi

readonly VERSION=$1
readonly GITREF=$2
readonly RELEASE_TAG="v$VERSION"
readonly CURRENT_BRANCH=$(git branch | grep "^\*" | cut -d' ' -f2)

# Make sure we are up to date
git checkout master
git pull --rebase

# Create a proper release tag

git tag -a "$RELEASE_TAG" -m "Release version $VERSION" $GITREF
git push origin "$RELEASE_TAG"

git reset --hard HEAD
git checkout $CURRENT_BRANCH

