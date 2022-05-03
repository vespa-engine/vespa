// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/sanitizers.h>
#include <vespa/vespalib/process/process.h>

using namespace vespalib;

TEST("that uncaught exception causes negative exitcode.") {
    Process proc("ulimit -c 0 && exec ./vespalib_caught_uncaught_app uncaught");
    EXPECT_LESS(proc.join(), 0);
}

TEST("that uncaught silenced exception causes exitcode 66") {
    Process proc("exec ./vespalib_caught_uncaught_app silenced_and_uncaught");
    EXPECT_EQUAL(proc.join(), 66);
}

TEST("that caught silenced exception followed by an uncaught causes negative exitcode.") {
    Process proc("ulimit -c 0 && exec ./vespalib_caught_uncaught_app uncaught_after_silenced_and_caught");
    EXPECT_LESS(proc.join(), 0);
}

TEST("that caught silenced exception causes exitcode 0") {
    Process proc("exec ./vespalib_caught_uncaught_app silenced_and_caught");
    EXPECT_EQUAL(proc.join(), 0);
}

#ifndef VESPA_USE_SANITIZER

#ifdef __APPLE__
// setrlimit with RLIMIT_AS is broken on Darwin
#else
TEST("that mmap within limits are fine cause exitcode 0") {
    Process proc("exec ./vespalib_mmap_app 150000000 10485760 1");
    EXPECT_EQUAL(proc.join(), 0);
}

TEST("that mmap beyond limits cause negative exitcode.") {
    Process proc("ulimit -c 0 && exec ./vespalib_mmap_app 100000000 10485760 10");
    EXPECT_LESS(proc.join(), 0);
}

TEST("that mmap beyond limits with set VESPA_SILENCE_CORE_ON_OOM cause exitcode 66.") {
    Process proc("VESPA_SILENCE_CORE_ON_OOM=1 exec ./vespalib_mmap_app 100000000 10485760 10");
    EXPECT_EQUAL(proc.join(), 66);
}

#endif
#endif

TEST_MAIN() { TEST_RUN_ALL(); }
