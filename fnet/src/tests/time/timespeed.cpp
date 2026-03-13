// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/benchmark_timer.h>

#include <chrono>

using vespalib::BenchmarkTimer;

TEST(TimeSpeedTest, steady_clock_speed) {
    using clock = std::chrono::steady_clock;
    clock::time_point t;
    double            min_time_us = BenchmarkTimer::benchmark([&t]() noexcept { t = clock::now(); }, 1.0) * 1000000.0;
    fprintf(stderr, "approx overhead per sample (steady clock): %f us\n", min_time_us);
}

TEST(TimeSpeedTest, system_clock_speed) {
    using clock = std::chrono::system_clock;
    clock::time_point t;
    double            min_time_us = BenchmarkTimer::benchmark([&t]() noexcept { t = clock::now(); }, 1.0) * 1000000.0;
    fprintf(stderr, "approx overhead per sample (system clock): %f us\n", min_time_us);
}

GTEST_MAIN_RUN_ALL_TESTS()
