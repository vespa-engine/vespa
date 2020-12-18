#!/bin/bash

if [[ $TRAVIS_PULL_REQUEST == false ]]; then
    export SHOULD_BUILD=all
    exit 0
fi

# Future use 
#JSON=$(curl -sLf https://api.github.com/repos/$TRAVIS_REPO_SLUG/pulls/$TRAVIS_PULL_REQUEST)
#PR_TITLE=$(jq -re '.title' <<< "$JSON")

JSON=$(curl -sLf https://api.github.com/repos/$TRAVIS_REPO_SLUG/pulls/$TRAVIS_PULL_REQUEST/commits)
COMMITS=$(jq -re '.[].sha' <<< "$JSON")

FILES=$(for C in $COMMITS; do JSON=$(curl -sLf https://api.github.com/repos/$TRAVIS_REPO_SLUG/commits/$C); jq -re '.files[].filename' <<< "$JSON"; done)

if [[ -z $FILES ]]; then
  SHOULD_BUILD=all
elif ! grep -v -E "(.h|.hh|.hxx|.c|.cpp|.cxx)$" <<< "$FILES" &> /dev/null; then
  SHOULD_BUILD=cpp
elif ! grep -v -E "(.java)$" <<< "$FILES" &> /dev/null; then
  SHOULD_BUILD=java
else
  SHOULD_BUILD=all
fi

export SHOULD_BUILD

