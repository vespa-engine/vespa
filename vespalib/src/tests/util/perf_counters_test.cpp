// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/perf_counters.h>
#include <gtest/gtest.h>
#include <chrono>
#include <cstdio>
#include <thread>

namespace vespalib {

// Testing stuff built around perf_event is tricky at the best of times since
// support depends on OS, kernel configuration, CPU architecture and model and
// virtualization restrictions. The one counter most likely to exist is the
// CPU high resolution timer, which is considered a software-level event. But
// we still dare not fail the test if we cannot get a counter for this event.

TEST(PerfCountersTest, can_sample_cpu_clock_if_supported) {
    if (!PerfCounters::is_supported()) {
        fprintf(stderr, "perf_event not supported on this system; not testing\n");
        return;
    }
    constexpr auto my_event = PerfCounters::Event::SW_CPU_CLOCK;
    PerfCounters pc({my_event});
    if (!pc.any_valid()) {
        fprintf(stderr, "Unable to create perf_event file descriptors (likely kernel restrictions); not testing\n");
        return;
    }
    // _Now_ we should at least be able to use the thing.
    EXPECT_TRUE(pc.valid(my_event));
    // Counters should be zero initially
    EXPECT_EQ(pc.get(my_event), 0);
    pc.start();
    std::this_thread::sleep_for(std::chrono::microseconds(100));
    pc.stop();
    EXPECT_GT(pc.get(my_event), 0);
}

} // namespace vespalib
