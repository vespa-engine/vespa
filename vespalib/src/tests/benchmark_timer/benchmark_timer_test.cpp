// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <thread>

using namespace vespalib;

TEST(BenchmarkTimerTest, require_that_the_benchmark_timer_can_be_used_as_advertised) {
    BenchmarkTimer timer(1.0);
    while (timer.has_budget()) {
        timer.before();
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        timer.after();
    }
    EXPECT_TRUE(timer.min_time() >= 0.0);
    fprintf(stderr, "5 ms sleep takes: %g ms\n", timer.min_time() * 1000.0);
}

TEST(BenchmarkTimerTest, require_that_the_benchmark_timer_all_in_one_benchmarking_works) {
    uint32_t sleep_time = 5;
    double t = BenchmarkTimer::benchmark([sleep_time](){std::this_thread::sleep_for(std::chrono::milliseconds(sleep_time));}, 1.0);
    fprintf(stderr, "5 ms sleep takes: %g ms\n", t * 1000.0);
}

TEST(BenchmarkTimerTest, require_that_the_benchmark_timer_all_in_one_benchmarking_with_baseline_works) {
    uint32_t work_time = 10;
    uint32_t baseline_time = 5;
    double t = BenchmarkTimer::benchmark([&](){std::this_thread::sleep_for(std::chrono::milliseconds(work_time));},
                                         [&](){std::this_thread::sleep_for(std::chrono::milliseconds(baseline_time));}, 1.0);
    fprintf(stderr, "10 ms sleep - 5 ms sleep takes: %g ms\n", t * 1000.0);
}

TEST(BenchmarkTimerTest, require_that_the_benchmark_timer_all_in_one_benchmarking_with_baseline_and_specified_loop_count_works) {
    uint32_t work_time = 2;
    uint32_t baseline_time = 1;
    uint32_t loop_cnt = 0;
    double t = BenchmarkTimer::benchmark([&](){std::this_thread::sleep_for(std::chrono::milliseconds(work_time)); ++loop_cnt;},
                                         [&](){std::this_thread::sleep_for(std::chrono::milliseconds(baseline_time));}, 7, 0.0);
    EXPECT_EQ(loop_cnt, 7u);
    fprintf(stderr, "2 ms sleep - 1 ms sleep takes: %g ms\n", t * 1000.0);
}

GTEST_MAIN_RUN_ALL_TESTS()
