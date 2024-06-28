#!/bin/bash

set -euo pipefail

if [[ $# -lt 5 ]]; then
    echo "Usage: $0 <Pipeline slug> <Github branch> <Vespa version> <Vespa gitref>"
    exit 1
fi

PIPELINE=$1
BRANCH=$2
VERSION=$3
GITREF=$4

curl  -X POST "https://api.buildkite.com/v2/organizations/vespaai/pipelines/$PIPELINE/builds" \
      -H "Content-Type: application/json" -H "Authorization: Bearer $BUILDKITE_TRIGGER_TOKEN" \
      -d "{
    \"commit\": \"HEAD\",
    \"branch\": \"$BRANCH\",
    \"message\": \"Vespa $VERSION release trigged from Screwdriver :vespa:\",
    \"ignore_pipeline_branch_filters\": true,
    \"env\": {
      \"VESPA_VERSION\": \"$VERSION\",
      \"VESPA_REF\": \"$GITREF\"
    }
  }"
