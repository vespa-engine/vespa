// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/slaveproc.h>

using namespace vespalib;

TEST("that uncaught exception causes exitcode 1") {
    SlaveProc proc("./vespalib_caught_uncaught_app uncaught");
    proc.wait();
    EXPECT_LESS(proc.getExitCode(), 0);
}

TEST("that uncaught silenced exception causes exitcode 66") {
    SlaveProc proc("./vespalib_caught_uncaught_app silenced_and_uncaught");
    proc.wait();
    EXPECT_EQUAL(proc.getExitCode(), 66);
}

TEST("that caught silenced exception causes exitcode 0") {
    SlaveProc proc("./vespalib_caught_uncaught_app silenced_and_caught");
    proc.wait();
    EXPECT_EQUAL(proc.getExitCode(), 0);
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
