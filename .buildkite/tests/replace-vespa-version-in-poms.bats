#!/usr/bin/env bats

#
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#

load "$BATS_PLUGIN_PATH/bats-assert/load.bash"
load "$BATS_PLUGIN_PATH/bats-mock/stub.bash"
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
  VESPA_VERSION="8.0.0"
}

teardown() {
  unset VESPA_VERSION
}

@test "Replace Vespa version in pom.xml files" {
  local test_dir="$BATS_TEST_TMPDIR/test-replace-vespa-version"

  mkdir -p "$test_dir/vespa/test"
  cat > "$test_dir/pom.xml" << 'EOF'
<project>
  <version>1.0-SNAPSHOT</version>
</project>
EOF

  cat > "$test_dir/vespa/pom.xml" << 'EOF'
<project>
  <vespaversion>${project.version}</vespaversion>
</project>
EOF

  cat > "$test_dir/vespa/test/pom.xml" << 'EOF'
<project>
  <test-framework.version>${project.version}</test-framework.version>
</project>
EOF

  run "$BATS_TEST_DIRNAME/../replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$test_dir"

  assert_success

  verify_file_content "$test_dir/pom.xml" "<project><version>$VESPA_VERSION</version></project>"
  verify_file_content "$test_dir/vespa/pom.xml" "<project><vespaversion>$VESPA_VERSION</vespaversion></project>"
  verify_file_content "$test_dir/vespa/test/pom.xml" "<project><test-framework.version>$VESPA_VERSION</test-framework.version></project>"
}

@test "No changes if version is not a snapshot" {
  local test_dir="$BATS_TEST_TMPDIR/test-no-change-version"

  mkdir -p "$test_dir"
  echo "<project><version>$VESPA_VERSION</version></project>" > "$test_dir/pom.xml"

  run "$BATS_TEST_DIRNAME/../replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$test_dir"

  assert_success
  verify_file_content "$test_dir/pom.xml" "<project><version>$VESPA_VERSION</version></project>"
}

@test "No changes if vespaversion is not project.version" {
  local test_dir="$BATS_TEST_TMPDIR/test-no-change-vespaversion"

  mkdir -p "$test_dir"
  echo "<project><vespaversion>1.0</vespaversion></project>" > "$test_dir/pom.xml"

  run "$BATS_TEST_DIRNAME/../replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$test_dir"

  assert_success
  verify_file_content "$test_dir/pom.xml" "<project><vespaversion>1.0</vespaversion></project>"
}

# Helper function to verify file content
verify_file_content() {
  local file="$1"
  local expected="$2"
  local actual_content
  local expected_content
  actual_content=$(cat "$file" | tr -d '[:space:]')
  expected_content=$(echo "$expected" | tr -d '[:space:]')
  assert_equal "$actual_content" "$expected_content"
}

@test "No pom.xml files found" {
  local test_dir="$BATS_TEST_TMPDIR/test-no-pom-files"

  mkdir -p "$test_dir"
  touch "$test_dir/README.md"

  run "$BATS_TEST_DIRNAME/../replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$test_dir"

  assert_success
  assert_output "No pom.xml files found in $test_dir"
}

@test "No Vespa version provided" {
  local test_dir="$BATS_TEST_TMPDIR/test-no-vespa-version"

  mkdir -p "$test_dir"
  echo "<project><version>1.0-SNAPSHOT</version></project>" > "$test_dir/pom.xml"

  run "$BATS_TEST_DIRNAME/../replace-vespa-version-in-poms.sh" "$test_dir"

  assert_failure
  assert_output "Usage: replace-vespa-version-in-poms.sh <Vespa version> <path>"
}

@test "No path provided" {

  run "$BATS_TEST_DIRNAME/../replace-vespa-version-in-poms.sh" "$VESPA_VERSION"

  assert_failure
  assert_output "Usage: replace-vespa-version-in-poms.sh <Vespa version> <path>"
}

@test "Directory does not exist" {
  local non_existent_dir="$BATS_TEST_TMPDIR/non-existent-dir"

  run "$BATS_TEST_DIRNAME/../replace-vespa-version-in-poms.sh" "$VESPA_VERSION" "$non_existent_dir"

  assert_failure
  assert_output "Directory $non_existent_dir does not exist or is not a directory."
}
