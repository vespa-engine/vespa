// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/metrictimer.h>
#include <thread>

namespace metrics {

struct MetricTimerTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(MetricTimerTest);
    CPPUNIT_TEST(timer_duration_is_correct_for_double_value_metric);
    CPPUNIT_TEST(timer_duration_is_correct_for_long_value_metric);
    CPPUNIT_TEST_SUITE_END();

    void timer_duration_is_correct_for_double_value_metric();
    void timer_duration_is_correct_for_long_value_metric();

    template <typename MetricType>
    void do_test_metric_timer_for_metric_type();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MetricTimerTest);

using namespace std::literals::chrono_literals;

template <typename MetricType>
void MetricTimerTest::do_test_metric_timer_for_metric_type() {
    MetricTimer timer;
    MetricType metric("foo", {}, "");
    std::this_thread::sleep_for(5ms); // Guaranteed to be monotonic time
    timer.stop(metric);
    // getDoubleValue() is present for both long and double metric types
    CPPUNIT_ASSERT(metric.getDoubleValue("last") >= 5.0);
}

void MetricTimerTest::timer_duration_is_correct_for_double_value_metric() {
    do_test_metric_timer_for_metric_type<DoubleAverageMetric>();
}

void MetricTimerTest::timer_duration_is_correct_for_long_value_metric() {
    do_test_metric_timer_for_metric_type<LongAverageMetric>();
}

} // metrics

