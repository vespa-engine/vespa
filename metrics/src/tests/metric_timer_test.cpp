// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/metrictimer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <thread>

namespace metrics {

using namespace std::literals::chrono_literals;

template <typename MetricType>
void do_test_metric_timer_for_metric_type() {
    MetricTimer timer;
    MetricType metric("foo", {}, "");
    std::this_thread::sleep_for(5ms); // Guaranteed to be monotonic time
    timer.stop(metric);
    // getDoubleValue() is present for both long and double metric types
    EXPECT_GE(metric.getDoubleValue("last"), 5.0);
}

TEST(MetricTimerTest, timer_duration_is_correct_for_double_value_metric) {
    do_test_metric_timer_for_metric_type<DoubleAverageMetric>();
}

TEST(MetricTimerTest, timer_duration_is_correct_for_long_value_metric) {
    do_test_metric_timer_for_metric_type<LongAverageMetric>();
}

}

