#!/usr/bin/env bats

#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#

load "$BATS_PLUGIN_PATH/bats-assert/load.bash"
load "$BATS_PLUGIN_PATH/bats-support/load.bash"

setup_file() {
  # Echo the name of the test file, to get prettier output from github actions
  test=$(basename "$BATS_TEST_FILENAME")
  if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
    # Print the test name in blue, bold, and underlined
    printf "%s%-80s%s\n" "$(tput setf 1)$(tput bold)$(tput smul)" "$test" "$(tput sgr0)" >&3
  fi
}

setup() {
  SCRIPT="$BATS_TEST_DIRNAME/../failure-summary.sh"
  LOG_FILE="$BATS_TEST_TMPDIR/step.log"
  unset BUILDKITE_LABEL BUILDKITE_BUILD_URL BUILDKITE_JOB_ID
}

# Prints the code block lines of the section with the given title in $output
section_lines() {
  awk -v title="**$1**" '$0 == title { found = 1; next }
                         found && /^```term$/ { in_block = 1; next }
                         in_block && /^```$/ { exit }
                         in_block { print }' <<< "$output"
}

@test "Fail with usage when arguments are missing" {
  run "$SCRIPT" java

  assert_failure
  assert_output --partial "Usage:"
}

@test "Summarize failing Surefire tests and the failing Maven module" {
  cat > "$LOG_FILE" << 'EOF'
--- ☕ Building Java components
[INFO] Running ai.vespa.ecommerce.FacetingTest
[ERROR] Tests run: 2, Failures: 1, Errors: 1, Skipped: 0, Time elapsed: 0.412 s <<< FAILURE! -- in ai.vespa.ecommerce.FacetingTest
[ERROR] ai.vespa.ecommerce.FacetingTest.testCounts -- Time elapsed: 0.021 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: <3> but was: <2>
	at ai.vespa.ecommerce.FacetingTest.testCounts(FacetingTest.java:42)

[INFO] Results:
[INFO]
[ERROR] Failures:
[ERROR]   FacetingTest.testCounts:42 expected: <3> but was: <2>
[ERROR] Errors:
[ERROR]   FacetingTest.testEmpty:17 » NullPointer
[INFO]
[ERROR] Tests run: 2, Failures: 1, Errors: 1, Skipped: 0
[INFO] BUILD FAILURE
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.2:test (default-test) on project ecommerce-faceting-beta: There are test failures.
[ERROR] -> [Help 1]
Command exited with non-zero status 1
	Command being timed: "/workspace/.buildkite/java.sh"
	User time (seconds): 599.22
	Exit status: 1
EOF

  run "$SCRIPT" java "$LOG_FILE"

  assert_success
  assert_line '**Failing tests**'
  assert_line 'FacetingTest.testCounts:42 expected: <3> but was: <2>'
  assert_line 'FacetingTest.testEmpty:17 » NullPointer'
  assert_line '**Maven**'
  assert_line --partial 'on project ecommerce-faceting-beta: There are test failures.'
  assert_line '**Test failure details**'
  assert_line 'org.opentest4j.AssertionFailedError: expected: <3> but was: <2>'
  assert_line '<summary>Last 40 lines of the log</summary>'
  refute_line '**Compilation errors**'
  refute_output --partial 'Command exited with non-zero status'
  refute_output --partial 'Command being timed'
  refute_output --partial 'User time'
}

@test "Fall back to individual Surefire failure lines when there is no result summary" {
  cat > "$LOG_FILE" << 'EOF'
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.1 s <<< FAILURE! -- in com.yahoo.FooTest
[ERROR] com.yahoo.FooTest.testBar -- Time elapsed: 0.01 s <<< FAILURE!
java.lang.AssertionError
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.2:test (default-test) on project foo: The forked VM terminated without properly saying goodbye.
EOF

  run "$SCRIPT" java "$LOG_FILE"

  assert_success
  assert_equal "$(section_lines 'Failing tests')" '[ERROR] com.yahoo.FooTest.testBar -- Time elapsed: 0.01 s <<< FAILURE!'
}

@test "Summarize Java compilation errors" {
  cat > "$LOG_FILE" << 'EOF'
[INFO] BUILD FAILURE
[ERROR] COMPILATION ERROR :
[ERROR] /workspace/config-model/src/main/java/com/yahoo/Foo.java:[12,5] cannot find symbol
[ERROR] /workspace/config-model/src/main/java/com/yahoo/Foo.java:[12,5] cannot find symbol
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile (default-compile) on project config-model: Compilation failure
EOF

  run "$SCRIPT" java "$LOG_FILE"

  assert_success
  # Duplicates are removed
  assert_equal "$(section_lines 'Compilation errors')" '[ERROR] /workspace/config-model/src/main/java/com/yahoo/Foo.java:[12,5] cannot find symbol'
  assert_line --partial 'on project config-model: Compilation failure'
}

@test "Summarize C++ compilation errors and failing build targets" {
  cat > "$LOG_FILE" << 'EOF'
--- ⚙️ Building C++ components
[1234/5678] Building CXX object searchlib/src/vespa/searchlib/CMakeFiles/foo.dir/bar.cpp.o
FAILED: searchlib/src/vespa/searchlib/CMakeFiles/foo.dir/bar.cpp.o
/workspace/searchlib/src/vespa/searchlib/bar.cpp:42:13: error: 'baz' was not declared in this scope
   42 |     return baz(x);
      |            ^~~
ninja: build stopped: subcommand failed.
EOF

  run "$SCRIPT" cpp "$LOG_FILE"

  assert_success
  assert_line '**Compilation errors**'
  assert_line "/workspace/searchlib/src/vespa/searchlib/bar.cpp:42:13: error: 'baz' was not declared in this scope"
  assert_line 'FAILED: searchlib/src/vespa/searchlib/CMakeFiles/foo.dir/bar.cpp.o'
}

@test "Summarize failing CTest tests" {
  cat > "$LOG_FILE" << 'EOF'
99% tests passed, 2 tests failed out of 3000

The following tests FAILED:
	 12 - searchlib_foo_test_app (Failed)
	345 - vespalib_bar_test_app (Timeout)
Errors while running CTest
EOF

  run "$SCRIPT" cpp-test "$LOG_FILE"

  assert_success
  assert_line '**Failing tests**'
  assert_line $'\t 12 - searchlib_foo_test_app (Failed)'
  assert_line $'\t345 - vespalib_bar_test_app (Timeout)'
}

@test "Summarize failing Go tests" {
  cat > "$LOG_FILE" << 'EOF'
=== RUN   TestFoo
    foo_test.go:12: expected 1, got 2
--- FAIL: TestFoo (0.00s)
FAIL	github.com/vespa-engine/vespa/client/go/internal/foo	0.012s
EOF

  run "$SCRIPT" go "$LOG_FILE"

  assert_success
  assert_line '--- FAIL: TestFoo (0.00s)'
  assert_line $'FAIL\tgithub.com/vespa-engine/vespa/client/go/internal/foo\t0.012s'
}

@test "Show the end of the log, expanded, when no known errors are found" {
  {
    for i in $(seq 1 100); do echo "line $i"; done
    echo "Something unexpected went wrong"
  } > "$LOG_FILE"

  run "$SCRIPT" publish-artifacts "$LOG_FILE"

  assert_success
  assert_line '**Last 40 lines of the log**'
  assert_line 'Something unexpected went wrong'
  assert_line 'line 62'
  refute_line 'line 61'
  refute_output --partial '<details>'
}

@test "Close the code block on its own line when the log does not end with a newline" {
  printf 'Unexpected failure' > "$LOG_FILE"

  run "$SCRIPT" publish-artifacts "$LOG_FILE"

  assert_success
  assert_line 'Unexpected failure'
  assert_line '```'
}

@test "Strip ANSI color codes" {
  printf '\e[1;31m[ERROR]\e[m Failed to execute goal foo on project bar: Boom\n' > "$LOG_FILE"

  run "$SCRIPT" java "$LOG_FILE"

  assert_success
  assert_line '[ERROR] Failed to execute goal foo on project bar: Boom'
}

@test "Use the Buildkite step label and link to the job log in the heading" {
  echo "boom" > "$LOG_FILE"
  export BUILDKITE_LABEL="Build Vespa 8 on AlmaLinux 8/amd64"
  export BUILDKITE_BUILD_URL="https://buildkite.com/vespaai/vespa-engine-vespa/builds/123"
  export BUILDKITE_JOB_ID="0199a1b2-c3d4"

  run "$SCRIPT" java "$LOG_FILE"

  assert_success
  assert_line --index 0 '**❌ Build Vespa 8 on AlmaLinux 8/amd64: step `java` failed** · [Open job log](https://buildkite.com/vespaai/vespa-engine-vespa/builds/123#0199a1b2-c3d4)'
}

@test "Omit the job log link outside Buildkite" {
  echo "boom" > "$LOG_FILE"

  run "$SCRIPT" java "$LOG_FILE"

  assert_success
  assert_line --index 0 '**❌ Build: step `java` failed**'
}

# Asserts that --short describes a log with the given content as the given description
assert_short_description() {
  printf '%s\n' "$1" > "$LOG_FILE"

  run "$SCRIPT" --short java "$LOG_FILE"

  assert_success
  assert_output "$2"
}

@test "Describe Java compilation errors in short" {
  assert_short_description '[ERROR] /workspace/foo/src/main/java/Foo.java:[12,5] cannot find symbol' 'Java build failed'
  assert_short_description "/workspace/foo/src/main/java/Foo.java:12: error: bad use of '>'" 'Java build failed'
}

@test "Describe C++ compilation errors in short" {
  assert_short_description "/workspace/searchlib/bar.cpp:42:13: error: 'baz' was not declared in this scope" 'C++ build failed'
  assert_short_description 'FAILED: searchlib/CMakeFiles/foo.dir/bar.cpp.o' 'C++ build failed'
}

@test "Describe failing tests in short" {
  assert_short_description $'[ERROR] Failures: \n[ERROR]   FooTest.testBar:42 expected: <3> but was: <2>' 'Java tests failed'
  assert_short_description $'The following tests FAILED:\n\t 12 - searchlib_foo_test_app (Failed)' 'C++ tests failed'
  assert_short_description '--- FAIL: TestFoo (0.00s)' 'Go tests failed'
}

@test "Describe compilation errors rather than failing tests in short" {
  assert_short_description $'[ERROR] /src/Foo.java:[12,5] cannot find symbol\n[ERROR] Failures: \n[ERROR]   FooTest.testBar:42 boom' 'Java build failed'
}

@test "Describe unknown failures in short without any details from the log" {
  assert_short_description 'Something unexpected went wrong' 'Build failed'
}
