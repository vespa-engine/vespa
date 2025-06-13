// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/process/process.h>

using namespace vespalib;

TEST(SilenceUncaughtTest, that_uncaught_exception_causes_negative_exitcode) {
    Process proc("ulimit -c 0 && exec ./vespalib_caught_uncaught_app uncaught");
    EXPECT_LT(proc.join(), 0);
}

TEST(SilenceUncaughtTest, that_uncaught_silenced_exception_causes_exitcode_66) {
    Process proc("exec ./vespalib_caught_uncaught_app silenced_and_uncaught");
    EXPECT_EQ(proc.join(), 66);
}

TEST(SilenceUncaughtTest, that_caught_silenced_exception_followed_by_an_uncaught_causes_negative_exitcode) {
    Process proc("ulimit -c 0 && exec ./vespalib_caught_uncaught_app uncaught_after_silenced_and_caught");
    EXPECT_LT(proc.join(), 0);
}

TEST(SilenceUncaughtTest, that_caught_silenced_exception_causes_exitcode_0) {
    Process proc("exec ./vespalib_caught_uncaught_app silenced_and_caught");
    EXPECT_EQ(proc.join(), 0);
}

#ifndef VESPA_USE_SANITIZER

#ifdef __APPLE__
// setrlimit with RLIMIT_AS is broken on Darwin
#else
TEST(SilenceUncaughtTest, that_mmap_within_limits_are_fine_cause_exitcode_0) {
    Process proc("exec ./vespalib_mmap_app 536870912 10485760 1");
    EXPECT_EQ(proc.join(), 0);
}

TEST(SilenceUncaughtTest, that_mmap_beyond_limits_cause_negative_exitcode) {
    Process proc("ulimit -c 0 && exec ./vespalib_mmap_app 100000000 10485760 10");
    EXPECT_LT(proc.join(), 0);
}

TEST(SilenceUncaughtTest, that_mmap_beyond_limits_with_set_VESPA_SILENCE_CORE_ON_OOM_cause_exitcode_66) {
    Process proc("VESPA_SILENCE_CORE_ON_OOM=1 exec ./vespalib_mmap_app 100000000 10485760 10");
    EXPECT_EQ(proc.join(), 66);
}

#endif
#endif

GTEST_MAIN_RUN_ALL_TESTS()
