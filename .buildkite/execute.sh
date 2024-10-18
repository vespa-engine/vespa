#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -euo pipefail

if [[ $# != 1 ]]; then
  echo "Usage: $0 <Step name>"  
  exit 1
fi

readonly MYDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd )"
readonly STEP=$1
readonly VERBOSE=${VERBOSE:-}

function report()
{
  echo "Reporting...."
  if [[ $BUILDKITE == true ]]; then
      if [[ -f $LOG_DIR/error-$STEP.log ]]; then
          tail -100 "$LOG_DIR/error-$STEP.log" | head -80 \
          | buildkite-agent annotate --style 'error' --context 'ctx-error'
      fi
  fi
}
trap report EXIT

echo "Executing $STEP"
START=$(date '+%s')
/usr/bin/time -v -p "$MYDIR/$STEP.sh" &> "$LOG_DIR/$STEP.log" || (cp -a "$LOG_DIR/$STEP.log" "$LOG_DIR/error-$STEP.log" && cat "$LOG_DIR/$STEP.log" && false)

if [[ -n $VERBOSE ]]; then
    cat "$LOG_DIR/$STEP.log"
fi

DURATION=$(( $(date '+%s') - START ))
echo "STEPTIMER=$STEP:$START,${DURATION}s"
echo "Finished $STEP in $DURATION seconds. Log saved in $LOG_DIR/$STEP.log."

