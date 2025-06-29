// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/metrics/simple_metrics.h>
#include <vespa/vespalib/metrics/simple_metrics_manager.h>
#include <vespa/vespalib/metrics/stable_store.h>
#include <vespa/vespalib/metrics/json_formatter.h>
#include <vespa/vespalib/metrics/prometheus_formatter.h>
#include "mock_tick.h"
#include <stdio.h>
#include <unistd.h>

using namespace vespalib;
using namespace vespalib::metrics;

TEST(SimpleMetricsTest, require_that_simple_metrics_gauge_merge_works)
{
    std::pair<MetricId, Point> id(MetricId(42), Point(17));
    Gauge::Measurement a1(id, 0.0);
    Gauge::Measurement b1(id, 7.0);
    Gauge::Measurement b2(id, 9.0);
    Gauge::Measurement b3(id, 8.0);
    Gauge::Measurement c1(id, 10.0);
    Gauge::Measurement c2(id, 1.0);

    GaugeAggregator a(a1), b(b1), c(c1);
    b.merge(b2);
    b.merge(b3);
    c.merge(c2);

    EXPECT_EQ(a.observedCount, 1u);
    EXPECT_EQ(a.sumValue, 0.0);
    EXPECT_EQ(a.minValue, 0.0);
    EXPECT_EQ(a.maxValue, 0.0);
    EXPECT_EQ(a.lastValue, 0.0);

    EXPECT_EQ(b.observedCount, 3u);
    EXPECT_EQ(b.sumValue, 24.0);
    EXPECT_EQ(b.minValue, 7.0);
    EXPECT_EQ(b.maxValue, 9.0);
    EXPECT_EQ(b.lastValue, 8.0);

    EXPECT_EQ(c.observedCount, 2u);
    EXPECT_EQ(c.sumValue, 11.0);
    EXPECT_EQ(c.minValue, 1.0);
    EXPECT_EQ(c.maxValue, 10.0);
    EXPECT_EQ(c.lastValue, 1.0);

    a.minValue = 8;

    a.merge(b);
    EXPECT_EQ(a.observedCount, 4u);
    EXPECT_EQ(a.sumValue, 24.0);
    EXPECT_EQ(a.minValue, 7.0);
    EXPECT_EQ(a.maxValue, 9.0);
    EXPECT_EQ(a.lastValue, 8.0);

    a.merge(b);
    EXPECT_EQ(a.observedCount, 7u);
    EXPECT_EQ(a.sumValue, 48.0);
    EXPECT_EQ(a.minValue, 7.0);
    EXPECT_EQ(a.maxValue, 9.0);
    EXPECT_EQ(a.lastValue, 8.0);

    a.merge(c);
    EXPECT_EQ(a.observedCount, 9u);
    EXPECT_EQ(a.sumValue, 59.0);
    EXPECT_EQ(a.minValue, 1.0);
    EXPECT_EQ(a.maxValue, 10.0);
    EXPECT_EQ(a.lastValue, 1.0);
}

bool compare_json(const std::string &a, const std::string &b)
{
    using vespalib::Memory;
    using vespalib::slime::JsonFormat;

    Slime slimeA, slimeB;
    if (! JsonFormat::decode(a, slimeA)) {
fprintf(stderr, "bad json a:\n>>>%s\n<<<\n", a.c_str());
        return false;
    }
    if (! JsonFormat::decode(b, slimeB)) {
fprintf(stderr, "bad json b\n");
        return false;
    }
    if (!(slimeA == slimeB)) {
fprintf(stderr, "compares unequal:\n[A]\n%s\n[B]\n%s\n", a.c_str(), b.c_str());
    }
    return slimeA == slimeB;
}

void check_json(const std::string &actual)
{
    std::string expect = "{"
    "   snapshot: { from: 1, to: 4 },"
    "   values: [ { name: 'foo',"
    "       values: { count: 17, rate: 4.85714 }"
    "   }, {"
    "       name: 'foo',"
    "       dimensions: { chain: 'default', documenttype: 'music', thread: '0' },"
    "       values: { count: 4, rate: 1.14286 }"
    "   }, {"
    "       name: 'bar',"
    "       values: { count: 4, rate: 1.14286, average: 42, sum: 168, min: 41, max: 43, last: 42 }"
    "   }, {"
    "       name: 'bar',"
    "       dimensions: { chain: 'vespa', documenttype: 'blogpost', thread: '1' },"
    "       values: { count: 1, rate: 0.285714, average: 14, sum: 14, min: 14, max: 14, last: 14 }"
    "   }, {"
    "       name: 'bar',"
    "       dimensions: { chain: 'vespa', documenttype: 'blogpost', thread: '2' },"
    "       values: { count: 1, rate: 0.285714, average: 11, sum: 11, min: 11, max: 11, last: 11 }"
    "   } ]"
    "}";
    EXPECT_TRUE(compare_json(expect, actual));
}

void check_prometheus(const std::string &actual) {
    std::string expect = R"(foo 17 4500
foo{chain="default",documenttype="music",thread="0"} 4 4500
bar_count 4 4500
bar_count{chain="vespa",documenttype="blogpost",thread="1"} 1 4500
bar_count{chain="vespa",documenttype="blogpost",thread="2"} 1 4500
bar_sum 168 4500
bar_sum{chain="vespa",documenttype="blogpost",thread="1"} 14 4500
bar_sum{chain="vespa",documenttype="blogpost",thread="2"} 11 4500
bar_min 41 4500
bar_min{chain="vespa",documenttype="blogpost",thread="1"} 14 4500
bar_min{chain="vespa",documenttype="blogpost",thread="2"} 11 4500
bar_max 43 4500
bar_max{chain="vespa",documenttype="blogpost",thread="1"} 14 4500
bar_max{chain="vespa",documenttype="blogpost",thread="2"} 11 4500
)";
    EXPECT_EQ(expect, actual);
}


TEST(SimpleMetricsTest, use_simple_metrics_collector)
{
    using namespace vespalib::metrics;
    SimpleManagerConfig cf;
    cf.sliding_window_seconds = 5;
    std::shared_ptr<MockTick> ticker = std::make_shared<MockTick>(TimeStamp(1.0));
    auto manager = SimpleMetricsManager::createForTest(cf, std::make_unique<TickProxy>(ticker));

    Counter myCounter = manager->counter("foo", "no description");
    myCounter.add();
    myCounter.add(16);

    Gauge myGauge = manager->gauge("bar", "dummy description");
    myGauge.sample(42.0);
    myGauge.sample(41.0);
    myGauge.sample(43.0);
    myGauge.sample(42.0);

    EXPECT_EQ(1.0, ticker->give(TimeStamp(2.0)).count());

    Snapshot snap1 = manager->snapshot();
    EXPECT_EQ(1.0, snap1.startTime());
    EXPECT_EQ(2.0, snap1.endTime());

    EXPECT_EQ(1u, snap1.counters().size());
    EXPECT_EQ("foo", snap1.counters()[0].name());
    EXPECT_EQ(17u, snap1.counters()[0].count());

    EXPECT_EQ(1u, snap1.gauges().size());
    EXPECT_EQ("bar", snap1.gauges()[0].name());
    EXPECT_EQ(4u, snap1.gauges()[0].observedCount());
    EXPECT_EQ(41.0, snap1.gauges()[0].minValue());
    EXPECT_EQ(43.0, snap1.gauges()[0].maxValue());
    EXPECT_EQ(42.0, snap1.gauges()[0].lastValue());

    Point one = manager->pointBuilder()
            .bind("chain", "default")
            .bind("documenttype", "music")
            .bind("thread", "0").build();
    PointBuilder b2 = manager->pointBuilder();
    b2.bind("chain", "vespa")
      .bind("documenttype", "blogpost");
    b2.bind("thread", "1");
    Point two = b2.build();
    EXPECT_EQ(one.id(), 1u);
    EXPECT_EQ(two.id(), 2u);

    Point anotherOne = manager->pointBuilder()
            .bind("chain", "default")
            .bind("documenttype", "music")
            .bind("thread", "0");
    EXPECT_EQ(anotherOne.id(), 1u);

    Point three = manager->pointBuilder(two).bind("thread", "2");
    EXPECT_EQ(three.id(), 3u);

    myCounter.add(3, one);
    myCounter.add(one);
    myGauge.sample(14.0, two);
    myGauge.sample(11.0, three);

    EXPECT_EQ(2.0, ticker->give(TimeStamp(4.5)).count());

    Snapshot snap2 = manager->snapshot();
    EXPECT_EQ(1.0, snap2.startTime());
    EXPECT_EQ(4.5, snap2.endTime());
    EXPECT_EQ(2u, snap2.counters().size());
    EXPECT_EQ(3u, snap2.gauges().size());

    JsonFormatter fmt2(snap2);
    check_json(fmt2.asString());

    PrometheusFormatter fmt3(snap2);
    check_prometheus(fmt3.as_text_formatted());

    // flush sliding window
    for (int i = 5; i <= 10; ++i) {
        ticker->give(TimeStamp(i));
    }
    Snapshot snap3 = manager->snapshot();
    EXPECT_EQ(5.0, snap3.startTime());
    EXPECT_EQ(10.0, snap3.endTime());
    EXPECT_EQ(2u, snap3.counters().size());
    EXPECT_EQ(0u, snap3.counters()[0].count());
    EXPECT_EQ(0u, snap3.counters()[1].count());
    EXPECT_EQ(3u, snap3.gauges().size());
    EXPECT_EQ(0u, snap3.gauges()[0].observedCount());
    EXPECT_EQ(0u, snap3.gauges()[1].observedCount());
    EXPECT_EQ(0u, snap3.gauges()[2].observedCount());

    Snapshot snap4 = manager->totalSnapshot();
    EXPECT_EQ(1.0,    snap4.startTime());
    EXPECT_EQ(10.0,   snap4.endTime());
    EXPECT_EQ(2u,     snap4.counters().size());
    EXPECT_NE(0u, snap4.counters()[0].count());
    EXPECT_NE(0u, snap4.counters()[1].count());
    EXPECT_EQ(3u,     snap4.gauges().size());
    EXPECT_NE(0u, snap4.gauges()[0].observedCount());
    EXPECT_NE(0u, snap4.gauges()[1].observedCount());
    EXPECT_NE(0u, snap4.gauges()[2].observedCount());
}

GTEST_MAIN_RUN_ALL_TESTS()
