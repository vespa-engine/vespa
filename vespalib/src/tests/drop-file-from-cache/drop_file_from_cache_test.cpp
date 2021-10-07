// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/child_process.h>

using vespalib::ChildProcess;

TEST("no arguments") {
    ChildProcess drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache");
    drop.wait();
    EXPECT_EQUAL(1, drop.getExitCode());
}

TEST("file does not exist") {
    ChildProcess drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache not_exist");
    drop.wait();
    EXPECT_EQUAL(2, drop.getExitCode());
}

TEST("All is well") {
    ChildProcess drop("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache vespalib_drop_file_from_cache_test_app");
    drop.wait();
    EXPECT_EQUAL(0, drop.getExitCode());
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
