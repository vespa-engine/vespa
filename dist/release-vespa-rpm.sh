#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 2 ]; then
  echo "Usage: $0 <version> <git ref>"
  exit 1
fi

readonly VERSION=$1
readonly GITREF=$2
readonly DIST_DIR="dist"
readonly SPECFILE="${DIST_DIR}/vespa.spec"
readonly TITO_DIR="${DIST_DIR}/.tito"
readonly RPM_BRANCH="rpmbuild"
readonly CURRENT_BRANCH=$(git branch | grep "^\*" | cut -d' ' -f2)

# Make sure we are up to date
git checkout master
git pull --rebase

# Update the VERSION file on master to be the next releasable version
echo "$VERSION" | awk -F. '{print $1"."($2+1)".0"}' > VERSION
git commit -am "Updating VERSION file to next releasable minor version."
for i in 1 2 3; do
  if git push; then
    break;
  fi
  git pull --rebase
done

# Delete existing branch if exists and create new one
git push --delete origin $RPM_BRANCH &> /dev/null || true
git branch -D $RPM_BRANCH &> /dev/null || true 
git checkout -b $RPM_BRANCH $GITREF

# Tito expects spec file and .tito directory to be on root
git mv $TITO_DIR .
git mv $SPECFILE .

# Hide pom.xml to avoid tito doing anything to our pom.xml files
mv pom.xml pom.xml.hide

# Run tito to update spec file and tag
tito tag --use-version=$VERSION --no-auto-changelog

# Push changes and tag to branc
git push -u origin --follow-tags $RPM_BRANCH

# Trig the build on Copr
curl -X POST \
     -H "Content-type: application/json" \
     -H "X-GitHub-Event: create" \
     -d '{ "ref": "rpmbuild", "ref_type": "branch", "repository": { "clone_url": "https://github.com/vespa-engine/vespa.git" } }' \
     https://copr.fedorainfracloud.org/webhooks/github/8037/d1dd5867-b493-4647-a888-0c887e6087b3/

git reset --hard HEAD
git checkout $CURRENT_BRANCH

