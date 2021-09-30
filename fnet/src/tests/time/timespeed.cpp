// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <chrono>

using vespalib::BenchmarkTimer;

TEST("steady clock speed") {
    using clock = std::chrono::steady_clock;
    clock::time_point t;
    double min_time_us = BenchmarkTimer::benchmark([&t]() noexcept {t = clock::now();}, 1.0) * 1000000.0;
    fprintf(stderr, "approx overhead per sample (steady clock): %f us\n", min_time_us);
}

TEST("system clock speed") {
    using clock = std::chrono::system_clock;
    clock::time_point t;
    double min_time_us = BenchmarkTimer::benchmark([&t]() noexcept {t = clock::now();}, 1.0) * 1000000.0;
    fprintf(stderr, "approx overhead per sample (system clock): %f us\n", min_time_us);
}

TEST_MAIN() { TEST_RUN_ALL(); }
