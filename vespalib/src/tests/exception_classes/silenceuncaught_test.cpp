// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/slaveproc.h>

using namespace vespalib;

TEST("that uncaught exception causes negative exitcode.") {
    SlaveProc proc("ulimit -c 0 && exec ./vespalib_caught_uncaught_app uncaught");
    proc.wait();
    EXPECT_LESS(proc.getExitCode(), 0);
}

TEST("that uncaught silenced exception causes exitcode 66") {
    SlaveProc proc("exec ./vespalib_caught_uncaught_app silenced_and_uncaught");
    proc.wait();
    EXPECT_EQUAL(proc.getExitCode(), 66);
}

TEST("that caught silenced exception followed by an uncaught causes negative exitcode.") {
    SlaveProc proc("ulimit -c 0 && exec ./vespalib_caught_uncaught_app uncaught_after_silenced_and_caught");
    proc.wait();
    EXPECT_LESS(proc.getExitCode(), 0);
}

TEST("that caught silenced exception causes exitcode 0") {
    SlaveProc proc("exec ./vespalib_caught_uncaught_app silenced_and_caught");
    proc.wait();
    EXPECT_EQUAL(proc.getExitCode(), 0);
}

TEST("that mmap within limits are fine cause exitcode 0") {
    SlaveProc proc("exec ./vespalib_mmap_app 150000000 10485760 1");
    proc.wait();
    EXPECT_EQUAL(proc.getExitCode(), 0);
}

TEST("that mmap beyond limits cause negative exitcode.") {
    SlaveProc proc("ulimit -c 0 && exec ./vespalib_mmap_app 100000000 10485760 10");
    proc.wait();
    EXPECT_LESS(proc.getExitCode(), 0);
}

TEST("that mmap beyond limits with set VESPA_SILENCE_CORE_ON_OOM cause exitcode 66.") {
    SlaveProc proc("VESPA_SILENCE_CORE_ON_OOM=1 exec ./vespalib_mmap_app 100000000 10485760 10");
    proc.wait();
    EXPECT_EQUAL(proc.getExitCode(), 66);
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
