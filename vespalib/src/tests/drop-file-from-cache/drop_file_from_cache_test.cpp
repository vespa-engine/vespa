// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/process/process.h>

using vespalib::Process;

TEST("no arguments") {
    Process drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache");
    EXPECT_EQUAL(1, drop.join());
}

TEST("file does not exist") {
    Process drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache not_exist");
    EXPECT_EQUAL(2, drop.join());
}

TEST("All is well") {
    Process drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache vespalib_drop_file_from_cache_test_app");
    EXPECT_EQUAL(0, drop.join());
}

TEST_MAIN() { TEST_RUN_ALL(); }
