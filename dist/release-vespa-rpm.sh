#!/bin/bash
set -e

if [ $# -ne 1 ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

readonly VERSION=$1
readonly SPECFILE="dist/vespa.spec"
readonly RPM_BRANCH="rpmbuild"
readonly CURRENT_BRANCH=$(git branch | grep "^\*" | cut -d' ' -f2)

# Make sure we are up to date
git checkout master
git pull --rebase

# Delete existing branch if exists and create new one
git push origin :$RPM_BRANCH &> /dev/null || true
git branch -D $RPM_BRANCH &> /dev/null || true 
git checkout -b $RPM_BRANCH $VERSION

# Tito expects spec file to be on root
git mv $SPECFILE .

# Run tito to update spec file and tag
tito init
tito tag --use-version=$VERSION --no-auto-changelog

# Push changes and tag to branc
git push -u origin --follow-tags $RPM_BRANCH

git checkout $CURRENT_BRANCH

