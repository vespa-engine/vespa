#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if (( ${#BASH_SOURCE[@]} == 1 )); then
    echo "This script must be sourced."
    exit 1
fi

if [[ $SD_PULL_REQUEST == false ]]; then
    export SHOULD_BUILD=all
    return 0
fi

JSON=$(curl -sLf https://api.github.com/repos/vespa-engine/vespa/pulls/$SD_PULL_REQUEST)
PR_TITLE=$(jq -re '.title' <<< "$JSON")

JSON=$(curl -sLf https://api.github.com/repos/vespa-engine/vespa/pulls/$SD_PULL_REQUEST/commits)
COMMITS=$(jq -re '.[].sha' <<< "$JSON")

FILES=$(for C in $COMMITS; do JSON=$(curl -sLf https://api.github.com/repos/vespa-engine/vespa/commits/$C); jq -re '.files[].filename' <<< "$JSON"; done)

if ! [[ $PR_TITLE =~ \[skip-systemtest\] ]]; then
  SHOULD_BUILD=systemtest
elif [[ -z $FILES ]]; then
  SHOULD_BUILD=all
elif ! grep -v -E "(\.h|\.hh|\.hxx|\.c|\.cpp|\.cxx)$" <<< "$FILES" &> /dev/null; then
  SHOULD_BUILD=cpp
elif ! grep -v -E "(\.java)$" <<< "$FILES" &> /dev/null; then
  SHOULD_BUILD=java
elif ! grep -v -E "(\.go)$" <<< "$FILES" &> /dev/null; then
  SHOULD_BUILD=go
else
  SHOULD_BUILD=all
fi

export SHOULD_BUILD

