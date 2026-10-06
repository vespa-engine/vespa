#!/usr/bin/env bash
#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# Summarizes the errors in the log of a failed build step as Markdown, for a Buildkite annotation.
# With --short, prints only one of a fixed set of descriptions, e.g. "Java tests failed", for Factory.
# This is expected to never include anything from the log.
#
# NOTE: Copied between vespa-engine/vespa and vespaai/cloud (the latter without the copyright line). Keep in sync.
# Tested in vespa-engine/vespa.

set -o errexit
set -o nounset
set -o pipefail

SHORT=false
if [[ ${1:-} == --short ]]; then
    SHORT=true
    shift
fi
readonly SHORT

if [[ $# != 2 ]]; then
    echo "Usage: $0 [--short] <Step name> <Log file>" >&2
    exit 1
fi

readonly STEP=$1
readonly LOG_FILE=$2
readonly MAX_LINES=40
readonly MAX_DETAIL_LINES=150
readonly MAX_LINE_LENGTH=500
readonly FENCE="\`\`\`"

WORK_DIR=$(mktemp -d)
readonly WORK_DIR
trap 'rm -rf "$WORK_DIR"' EXIT
readonly LOG="$WORK_DIR/log"

# Strips ANSI codes and the /usr/bin/time report, and ensures the log ends with a newline.
clean_log() {
    sed -E "s/"$'\e'"\[[0-9;]*[A-Za-z]//g" "$LOG_FILE" | awk '{ print }' > "$LOG"

    local timed_line
    timed_line=$(grep -n -E '^[[:space:]]*Command being timed:' "$LOG" | tail -1 | cut -d: -f1 || true)
    if [[ -n $timed_line ]]; then
        head -n "$((timed_line - 1))" "$LOG" \
        | awk 'NR > 1 { print prev } { prev = $0 } END { if (NR > 0 && prev !~ /^Command exited with non-zero status/) print prev }' \
        > "$LOG.tmp"
        mv "$LOG.tmp" "$LOG"
    fi
}

# Removes duplicate lines.
dedupe() {
    awk '!seen[$0]++'
}

# Prints at most max_lines lines of a file in a terminal block.
code_block() {
    local file=$1
    local max_lines=$2

    local count
    count=$(wc -l < "$file" | tr -d ' ')
    printf '%sterm\n' "$FENCE"
    head -n "$max_lines" "$file" | cut -c "1-$MAX_LINE_LENGTH" | sed "s/$FENCE/'''/g"
    printf '%s\n' "$FENCE"
    if (( count > max_lines )); then
        printf '_... %d more lines, see the job log._\n' "$((count - max_lines))"
    fi
    printf '\n'
}

# Prints a titled section, if the file is non-empty.
section() {
    [[ -s $2 ]] || return 0
    printf '**%s**\n\n' "$1"
    code_block "$2" "$3"
}

# Like section, but collapsed.
collapsed_section() {
    [[ -s $2 ]] || return 0
    printf '<details>\n<summary>%s</summary>\n\n' "$1"
    code_block "$2" "$3"
    printf '</details>\n\n'
}

clean_log

# Java compilation and javadoc errors
grep -E '^\[ERROR\] .+\.(java|kt|scala):\[[0-9]+(,[0-9]+)?\]|\.java:[0-9]+: error: ' "$LOG" \
    | dedupe > "$WORK_DIR/compile-java" || true
# C++ compiler, linker and ninja errors
grep -E '\.(c|cc|cpp|cxx|h|hh|hpp)(:[0-9]+)+: (fatal )?error: |undefined reference to |^collect2: error: |^FAILED: ' "$LOG" \
    | dedupe > "$WORK_DIR/compile-cpp" || true
# Failed make targets
grep -E '^g?make(\[[0-9]+\])?: \*\*\* ' "$LOG" | dedupe > "$WORK_DIR/compile-make" || true
cat "$WORK_DIR/compile-java" "$WORK_DIR/compile-cpp" "$WORK_DIR/compile-make" > "$WORK_DIR/compile"

# Surefire result summary
awk '/^\[ERROR\] (Failures|Errors): *$/ { in_block = 1; next }
     in_block && /^\[ERROR\]   / { sub(/^\[ERROR\]   /, ""); print; next }
     { in_block = 0 }' "$LOG" | dedupe > "$WORK_DIR/tests-java"
# Individual Surefire failures, if there is no summary
if [[ ! -s $WORK_DIR/tests-java ]]; then
    grep -E '<<< (FAILURE|ERROR)!' "$LOG" | grep -v -E 'Tests run:' | dedupe > "$WORK_DIR/tests-java" || true
fi
# CTest failures
awk '/^The following tests FAILED:/ { in_block = 1; next }
     in_block && /^[[:space:]]*[0-9]+ - / { print; next }
     { in_block = 0 }' "$LOG" | dedupe > "$WORK_DIR/tests-cpp"
# Go tests
grep -E '^[[:space:]]*--- FAIL: |^FAIL[[:space:]]' "$LOG" | dedupe > "$WORK_DIR/tests-go" || true
cat "$WORK_DIR/tests-java" "$WORK_DIR/tests-cpp" "$WORK_DIR/tests-go" > "$WORK_DIR/tests"

# Surefire failures with message and start of stack trace
awk '/<<< (FAILURE|ERROR)!/ && !/Tests run:/ { print; remaining = 15; next }
     remaining > 0 && (/^\[/ || /^[[:space:]]*$/) { remaining = 0 }
     remaining > 0 { print; remaining-- }' "$LOG" > "$WORK_DIR/test-details"

# Maven goal failures, which name the failing module
grep -E '^\[ERROR\] Failed to execute goal ' "$LOG" | dedupe > "$WORK_DIR/maven" || true

tail -n "$MAX_LINES" "$LOG" > "$WORK_DIR/tail"

# Compilation errors first, as they are the cause of any failing tests
if [[ $SHORT == true ]]; then
    if [[ -s $WORK_DIR/compile-java ]]; then
        echo "Java build failed"
    elif [[ -s $WORK_DIR/compile-cpp ]]; then
        echo "C++ build failed"
    elif [[ -s $WORK_DIR/tests-java ]]; then
        echo "Java tests failed"
    elif [[ -s $WORK_DIR/tests-cpp ]]; then
        echo "C++ tests failed"
    elif [[ -s $WORK_DIR/tests-go ]]; then
        echo "Go tests failed"
    else
        echo "Build failed"
    fi
    exit 0
fi

# shellcheck disable=SC2016 # The backticks are Markdown
printf '**❌ %s: step `%s` failed**' "${BUILDKITE_LABEL:-Build}" "$STEP"
if [[ -n ${BUILDKITE_BUILD_URL:-} && -n ${BUILDKITE_JOB_ID:-} ]]; then
    printf ' · [Open job log](%s#%s)' "$BUILDKITE_BUILD_URL" "$BUILDKITE_JOB_ID"
fi
printf '\n\n'

section "Compilation errors" "$WORK_DIR/compile" "$MAX_LINES"
section "Failing tests" "$WORK_DIR/tests" "$MAX_LINES"
section "Maven" "$WORK_DIR/maven" "$MAX_LINES"
collapsed_section "Test failure details" "$WORK_DIR/test-details" "$MAX_DETAIL_LINES"

if [[ -s $WORK_DIR/compile || -s $WORK_DIR/tests || -s $WORK_DIR/maven ]]; then
    collapsed_section "Last $MAX_LINES lines of the log" "$WORK_DIR/tail" "$MAX_LINES"
else
    section "Last $MAX_LINES lines of the log" "$WORK_DIR/tail" "$MAX_LINES"
fi
