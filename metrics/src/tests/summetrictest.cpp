// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/metrics/metrics.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace metrics {

TEST(SumMetricTest, test_long_count_metric)
{
    MetricSet parent("parent", {}, "");
    SumMetric<LongCountMetric> sum("foo", {}, "foodesc", &parent);

    LongCountMetric v1("ff", {}, "", &parent);
    LongCountMetric v2("aa", {}, "", &parent);

    sum.addMetricToSum(v1);
    sum.addMetricToSum(v2);

        // Give them some values
    v1.inc(3);
    v2.inc(7);

        // Verify XML output. Should be in register order.
    std::string expected("foo count=10");
    EXPECT_EQ(expected, sum.toString());
    EXPECT_EQ(int64_t(10), sum.getLongValue("value"));
}

TEST(SumMetricTest, test_average_metric)
{
    MetricSet parent("parent", {}, "");
    SumMetric<LongAverageMetric> sum("foo", {}, "foodesc", &parent);

    LongAverageMetric v1("ff", {}, "", &parent);
    LongAverageMetric v2("aa", {}, "", &parent);

    sum.addMetricToSum(v1);
    sum.addMetricToSum(v2);

        // Give them some values
    v1.addValue(3);
    v2.addValue(7);

        // Verify XML output. Should be in register order.
    std::string expected("foo average=5 last=7 min=3 max=7 count=2 total=10");
    EXPECT_EQ(expected, sum.toString());
    EXPECT_EQ(int64_t(5), sum.getLongValue("value"));
    EXPECT_EQ(int64_t(3), sum.getLongValue("min"));
    EXPECT_EQ(int64_t(7), sum.getLongValue("max"));
}

TEST(SumMetricTest, test_metric_set)
{
    MetricSet parent("parent", {}, "");
    SumMetric<MetricSet> sum("foo", {}, "bar", &parent);

    MetricSet set1("a", {}, "", &parent);
    MetricSet set2("b", {}, "", &parent);
    LongValueMetric v1("c", {}, "", &set1);
    LongValueMetric v2("d", {}, "", &set2);
    LongCountMetric v3("e", {}, "", &set1);
    LongCountMetric v4("f", {}, "", &set2);

    sum.addMetricToSum(set1);
    sum.addMetricToSum(set2);

    // Give them some values
    v1.addValue(3);
    v2.addValue(7);
    v3.inc(2);
    v4.inc();

    // Verify XML output. Should be in register order.
    std::string expected("'\n"
            "foo:\n"
            "  c average=3 last=3 min=3 max=3 count=1 total=3\n"
            "  e count=2'"
    );
    EXPECT_EQ(expected, "'\n" + sum.toString() + "'");
}

TEST(SumMetricTest, test_remove)
{
    MetricSet parent("parent", {}, "");
    SumMetric<LongCountMetric> sum("foo", {}, "foodesc", &parent);

    LongCountMetric v1("ff", {}, "", &parent);
    LongCountMetric v2("aa", {}, "", &parent);
    LongCountMetric v3("zz", {}, "", &parent);

    sum.addMetricToSum(v1);
    sum.addMetricToSum(v2);
    sum.addMetricToSum(v3);

    // Give them some values
    v1.inc(3);
    v2.inc(7);
    v3.inc(10);

    EXPECT_EQ(int64_t(20), sum.getLongValue("value"));
    sum.removeMetricFromSum(v2);
    EXPECT_EQ(int64_t(13), sum.getLongValue("value"));
}

TEST(SumMetricTest, test_start_value)
{
    MetricSnapshot snapshot("active");
    SumMetric<LongValueMetric> sum("foo", {}, "foodesc", &snapshot.getMetrics());
    LongValueMetric start("start", {}, "", 0);
    start.set(50);
    sum.setStartValue(start);

    // without children
    EXPECT_EQ(int64_t(50), sum.getLongValue("value"));

    MetricSnapshot copy("copy");
    copy.recreateSnapshot(snapshot.getMetrics(), true);
    snapshot.addToSnapshot(copy, system_time(100s));

    LongValueMetric value("value", {}, "", &snapshot.getMetrics());
    sum.addMetricToSum(value);
    value.set(10);

    // with children
    EXPECT_EQ(int64_t(60), sum.getLongValue("value"));
}

namespace {

struct MetricSetWithSum : public MetricSet
{
    LongValueMetric _v1;
    LongValueMetric _v2;
    SumMetric<LongValueMetric> _sum;
    MetricSetWithSum();
    ~MetricSetWithSum() override;
};

MetricSetWithSum::MetricSetWithSum()
    : MetricSet("MetricSetWithSum", {}, ""),
      _v1("v1", {}, "", this),
      _v2("v2", {}, "", this),
      _sum("sum", {}, "", this)
{
    _sum.addMetricToSum(_v1);
    _sum.addMetricToSum(_v2);
}

MetricSetWithSum::~MetricSetWithSum() = default;

}

TEST(SumMetricTest, test_nested_sum)
{
    MetricSetWithSum w1;
    MetricSetWithSum w2;
    MetricSetWithSum sum;
    w1._v1.addValue(10);
    w1._v2.addValue(13);
    w2._v1.addValue(27);
    w2._v2.addValue(29);
    w1.addToPart(sum);
    w2.addToPart(sum);
    EXPECT_EQ(int64_t(37), sum._v1.getLongValue("value"));
    EXPECT_EQ(int64_t(42), sum._v2.getLongValue("value"));
    EXPECT_EQ(int64_t(79), sum._sum.getLongValue("value"));
}

}
