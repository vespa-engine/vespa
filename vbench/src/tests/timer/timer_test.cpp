// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/util/time.h>

using namespace vbench;

TEST(TimerTest, ignored_timer_test) {
    Timer timer;
    fprintf(stderr, "after create (should be ~0.0): %g\n", timer.sample());
    std::this_thread::sleep_for(1000ms);
    fprintf(stderr, "after 1000ms (should be ~1.0): %g\n", timer.sample());
    timer.reset();
    fprintf(stderr, "after reset (should be ~0.0): %g\n", timer.sample());
}

GTEST_MAIN_RUN_ALL_TESTS()
