// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/slaveproc.h>

using vespalib::SlaveProc;

TEST("simple run") {
    std::string out;
    EXPECT_TRUE(SlaveProc::run("../../apps/vespa-drop-file-from-cache/vespa-drop-file-from-cache", out));
    EXPECT_EQUAL(out, "foo");
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
