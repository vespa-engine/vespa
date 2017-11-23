// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/metrics/simple_metrics.h>
#include <vespa/vespalib/metrics/simple_metrics_manager.h>
#include <vespa/vespalib/metrics/no_realloc_bunch.h>
#include <vespa/vespalib/metrics/json_formatter.h>
#include <stdio.h>
#include <unistd.h>

using namespace vespalib;
using namespace vespalib::metrics;

TEST("require that simple metrics gauge merge works")
{
    MetricIdentifier id(42);
    GaugeAggregator a(id), b(id), c(id);
    b.observedCount = 3;
    b.sumValue = 24.0;
    b.minValue = 7.0;
    b.maxValue = 9.0;
    b.lastValue = 8.0;

    EXPECT_EQUAL(a.observedCount, 0u);
    EXPECT_EQUAL(a.sumValue, 0.0);
    EXPECT_EQUAL(a.minValue, 0.0);
    EXPECT_EQUAL(a.maxValue, 0.0);
    EXPECT_EQUAL(a.lastValue, 0.0);
    a.merge(b);
    EXPECT_EQUAL(a.observedCount, 3u);
    EXPECT_EQUAL(a.sumValue, 24.0);
    EXPECT_EQUAL(a.minValue, 7.0);
    EXPECT_EQUAL(a.maxValue, 9.0);
    EXPECT_EQUAL(a.lastValue, 8.0);
    a.merge(b);
    EXPECT_EQUAL(a.observedCount, 6u);
    EXPECT_EQUAL(a.sumValue, 48.0);
    EXPECT_EQUAL(a.minValue, 7.0);
    EXPECT_EQUAL(a.maxValue, 9.0);
    EXPECT_EQUAL(a.lastValue, 8.0);

    c.observedCount = 2;
    c.sumValue = 11.0;
    c.minValue = 1.0;
    c.maxValue = 10.0;
    c.lastValue = 1.0;

    a.merge(c);
    EXPECT_EQUAL(a.observedCount, 8u);
    EXPECT_EQUAL(a.sumValue, 59.0);
    EXPECT_EQUAL(a.minValue, 1.0);
    EXPECT_EQUAL(a.maxValue, 10.0);
    EXPECT_EQUAL(a.lastValue, 1.0);
}

struct Foo {
    int a;
    char *p;
    explicit Foo(int v) : a(v), p(nullptr) {}
    bool operator==(const Foo &other) const {
        return a == other.a;
    }
};

TEST("require that no_realloc_bunch works")
{
    vespalib::NoReallocBunch<Foo> bunch;
    bunch.add(Foo(1));
    bunch.add(Foo(2));
    bunch.add(Foo(3));
    bunch.add(Foo(5));
    bunch.add(Foo(8));
    bunch.add(Foo(13));
    bunch.add(Foo(21));
    bunch.add(Foo(34));
    bunch.add(Foo(55));
    bunch.add(Foo(89));

    EXPECT_EQUAL(bunch.size(), 10u);

    int sum = 0;

    bunch.apply([&sum](const Foo& value) { sum += value.a; });
    EXPECT_EQUAL(231, sum);

    int idx = bunch.lookup(Foo(6));
    EXPECT_EQUAL(-1, idx);

    idx = bunch.lookup(Foo(13));
    EXPECT_EQUAL(5, idx);

    const Foo& val = bunch.lookup(8);
    EXPECT_TRUE(Foo(55) == val);

    for (int i = 0; i < 20000; ++i) {
        bunch.add(Foo(i));
    }
    EXPECT_TRUE(Foo(19999) == bunch.lookup(20009));
}

TEST("use simple_metrics_collector")
{
    using namespace vespalib::metrics;
    SimpleManagerConfig cf;
    cf.sliding_window_seconds = 5;
    auto manager = SimpleMetricsManager::create(cf);
    Counter myCounter = manager->counter("foo");
    myCounter.add();
    myCounter.add(16);

    Gauge myGauge = manager->gauge("bar");
    myGauge.sample(42.0);
    myGauge.sample(41.0);
    myGauge.sample(43.0);
    myGauge.sample(42.0);

    Point one = manager->pointBuilder()
            .bind("chain", "default")
            .bind("documenttype", "music")
            .bind("thread", "0").build();
    Point two = manager->pointBuilder()
            .bind("chain", "vespa")
            .bind("documenttype", "blogpost")
            .bind("thread", "1");
    EXPECT_EQUAL(one.id(), 1u);
    EXPECT_EQUAL(two.id(), 2u);

    Point anotherOne = manager->pointBuilder()
            .bind("chain", "default")
            .bind("documenttype", "music")
            .bind("thread", "0");
    EXPECT_EQUAL(anotherOne.id(), 1u);

    Point three = manager->pointBuilder(two).bind("thread", "2");
    EXPECT_EQUAL(three.id(), 3u);

    myCounter.add(3, one);
    myCounter.add(one);
    myGauge.sample(14.0, two);
    myGauge.sample(11.0, three);

 // sleep(2);

    Snapshot snap = manager->snapshot();
    fprintf(stdout, "snap begin: %15f\n", snap.startTime());
    fprintf(stdout, "snap end: %15f\n", snap.endTime());

 // for (const auto& entry : snap.points()) {
 //     fprintf(stdout, "snap point: %zd dimension(s)\n", entry.dimensions.size());
 //     for (const auto& dim : entry.dimensions) {
 //         fprintf(stdout, "       label: [%s] = '%s'\n",
 //                 dim.dimensionName().c_str(), dim.labelValue().c_str());
 //     }
 // }
    for (const auto& entry : snap.counters()) {
        fprintf(stdout, "snap counter: '%s'\n", entry.name().c_str());
        for (const auto& dim : entry.point().dimensions) {
            fprintf(stdout, "       label: [%s] = '%s'\n",
                    dim.dimensionName().c_str(), dim.labelValue().c_str());
        }
        fprintf(stdout, "       count: %zd\n", entry.count());
    }
    for (const auto& entry : snap.gauges()) {
        fprintf(stdout, "snap gauge: '%s'\n", entry.name().c_str());
        for (const auto& dim : entry.point().dimensions) {
            fprintf(stdout, "       label: [%s] = '%s'\n",
                    dim.dimensionName().c_str(), dim.labelValue().c_str());
        }
        fprintf(stdout, "  observed: %zd\n", entry.observedCount());
        fprintf(stdout, "       avg: %f\n", entry.averageValue());
        fprintf(stdout, "       min: %f\n", entry.minValue());
        fprintf(stdout, "       max: %f\n", entry.maxValue());
        fprintf(stdout, "      last: %f\n", entry.lastValue());
    }

    JsonFormatter fmt(snap);
    fprintf(stdout, "JSON format:\n>>>\n%s\n<<<\n", fmt.asString().c_str());
}

TEST_MAIN() { TEST_RUN_ALL(); }
