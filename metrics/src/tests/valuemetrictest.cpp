// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/floatingpointtype.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/util/exceptions.h>

using vespalib::Double;

namespace metrics {

#define ASSERT_AVERAGE(metric, avg, min, max, count, last) \
    EXPECT_EQ(Double(avg), Double(metric.getAverage())); \
    EXPECT_EQ(Double(count), Double(metric.getCount())); \
    EXPECT_EQ(Double(last), Double(metric.getLast())); \
    if (metric.getCount() > 0) { \
    EXPECT_EQ(Double(min), Double(metric.getMinimum())); \
    EXPECT_EQ(Double(max), Double(metric.getMaximum())); \
    }

TEST(ValueMetricTest, test_double_value_metric)
{
    DoubleValueMetric m("test", {{"tag"}}, "description");
    m.addValue(100);
    ASSERT_AVERAGE(m, 100, 100, 100, 1, 100);
    m.addValue(100);
    ASSERT_AVERAGE(m, 100, 100, 100, 2, 100);
    m.addValue(40);
    ASSERT_AVERAGE(m, 80, 40, 100, 3, 40);
    DoubleValueMetric m2(m);
    ASSERT_AVERAGE(m, 80, 40, 100, 3, 40);
    m.reset();
    ASSERT_AVERAGE(m, 0, 0, 0, 0, 0);

    DoubleValueMetric n("m2", {}, "desc");
    n.addValue(60);
    ASSERT_AVERAGE(n, 60, 60, 60, 1, 60);

    DoubleValueMetric o = m2 + n;
    ASSERT_AVERAGE(o, 140, 40, 100, 4, 100);

    o = n + m2;
    ASSERT_AVERAGE(o, 140, 40, 100, 4, 100);

    std::string expected(
            "test average=80 last=40 min=40 max=100 count=3 total=240");
    EXPECT_EQ(expected, m2.toString());
    expected = "m2 average=140 last=100";
    EXPECT_EQ(expected, o.toString());

    EXPECT_EQ(Double(40), Double(m2.getDoubleValue("value")));
    EXPECT_EQ(Double(80), Double(m2.getDoubleValue("average")));
    EXPECT_EQ(Double(40), Double(m2.getDoubleValue("min")));
    EXPECT_EQ(Double(100), Double(m2.getDoubleValue("max")));
    EXPECT_EQ(Double(40), Double(m2.getDoubleValue("last")));
    EXPECT_EQ(Double(3), Double(m2.getDoubleValue("count")));
    EXPECT_EQ(Double(240), Double(m2.getDoubleValue("total")));

    EXPECT_EQ(int64_t(40), m2.getLongValue("value"));
    EXPECT_EQ(int64_t(80), m2.getLongValue("average"));
    EXPECT_EQ(int64_t(40), m2.getLongValue("min"));
    EXPECT_EQ(int64_t(100), m2.getLongValue("max"));
    EXPECT_EQ(int64_t(40), m2.getLongValue("last"));
    EXPECT_EQ(int64_t(3), m2.getLongValue("count"));
    EXPECT_EQ(int64_t(240), m2.getLongValue("total"));
}

TEST(ValueMetricTest, test_double_value_metric_not_updated_on_nan)
{
    DoubleValueMetric m("test", {{"tag"}}, "description");
    m.addValue(std::numeric_limits<double>::quiet_NaN());
    EXPECT_EQ(std::string(), m.toString());

    m.addAvgValueWithCount(std::numeric_limits<double>::quiet_NaN(), 123);
    EXPECT_EQ(std::string(), m.toString());

    m.inc(std::numeric_limits<double>::quiet_NaN());
    EXPECT_EQ(std::string(), m.toString());

    m.dec(std::numeric_limits<double>::quiet_NaN());
    EXPECT_EQ(std::string(), m.toString());
}

TEST(ValueMetricTest, test_double_value_metric_not_updated_on_infinity)
{
    DoubleValueMetric m("test", {{"tag"}}, "description");
    m.addValue(std::numeric_limits<double>::infinity());
    EXPECT_EQ(std::string(), m.toString());

    m.addAvgValueWithCount(std::numeric_limits<double>::quiet_NaN(), 123);
    EXPECT_EQ(std::string(), m.toString());

    m.inc(std::numeric_limits<double>::infinity());
    EXPECT_EQ(std::string(), m.toString());

    m.dec(std::numeric_limits<double>::infinity());
    EXPECT_EQ(std::string(), m.toString());
}

TEST(ValueMetricTest, test_long_value_metric)
{
    LongValueMetric m("test", {{"tag"}}, "description");
    m.addValue(100);
    ASSERT_AVERAGE(m, 100, 100, 100, 1, 100);
    m.addValue(100);
    ASSERT_AVERAGE(m, 100, 100, 100, 2, 100);
    m.addValue(41);
    ASSERT_AVERAGE(m, 241.0 / 3, 41, 100, 3, 41);
    LongValueMetric m2(m);
    ASSERT_AVERAGE(m, 241.0 / 3, 41, 100, 3, 41);
    m.reset();
    ASSERT_AVERAGE(m, 0, 0, 0, 0, 0);

    LongValueMetric n("m2", {}, "desc");
    n.addValue(60);
    ASSERT_AVERAGE(n, 60, 60, 60, 1, 60);

    LongValueMetric o = m2 + n;
    ASSERT_AVERAGE(o, 140.25, 41, 100, 4, 101);

    o = n + m2;
    ASSERT_AVERAGE(o, 140.25, 41, 100, 4, 101);

    std::string expected(
            "test average=80.3333 last=41 min=41 max=100 count=3 total=241");
    EXPECT_EQ(expected, m2.toString());
    expected = "m2 average=140.25 last=101";
    EXPECT_EQ(expected, o.toString());

    EXPECT_EQ(Double(41), Double(m2.getDoubleValue("value")));
    EXPECT_EQ(Double(241.0/3), Double(m2.getDoubleValue("average")));
    EXPECT_EQ(Double(41), Double(m2.getDoubleValue("min")));
    EXPECT_EQ(Double(100), Double(m2.getDoubleValue("max")));
    EXPECT_EQ(Double(41), Double(m2.getDoubleValue("last")));
    EXPECT_EQ(Double(3), Double(m2.getDoubleValue("count")));
    EXPECT_EQ(Double(241), Double(m2.getDoubleValue("total")));

    EXPECT_EQ(int64_t(41), m2.getLongValue("value"));
    EXPECT_EQ(int64_t(80), m2.getLongValue("average"));
    EXPECT_EQ(int64_t(41), m2.getLongValue("min"));
    EXPECT_EQ(int64_t(100), m2.getLongValue("max"));
    EXPECT_EQ(int64_t(41), m2.getLongValue("last"));
    EXPECT_EQ(int64_t(3), m2.getLongValue("count"));
    EXPECT_EQ(int64_t(241), m2.getLongValue("total"));
}

TEST(ValueMetricTest, test_small_average)
{
    DoubleValueMetric m("test", {{"tag"}}, "description");
    m.addValue(0.0001);
    m.addValue(0.0002);
    m.addValue(0.0003);
    std::vector<Metric::UP> ownerList;
    Metric::UP c(m.clone(ownerList, Metric::INACTIVE, 0, false));
    std::string expect("test average=0.0002 last=0.0003 min=0.0001 max=0.0003 count=3 total=0.0006");
    EXPECT_EQ(expect, m.toString());
    EXPECT_EQ(expect, c->toString());
}

TEST(ValueMetricTest, test_add_value_batch)
{
    DoubleValueMetric m("test", {{"tag"}}, "description");
    m.addValueBatch(100, 3, 80, 120);
    ASSERT_AVERAGE(m, 100, 80, 120, 3, 100);
    m.addValueBatch(123, 0, 12, 1234);
    ASSERT_AVERAGE(m, 100, 80, 120, 3, 100);
}

namespace {

vespalib::string extractMetricJson(vespalib::stringref s) {
    vespalib::StringTokenizer st(s, "\n", "");
    for (uint32_t i = st.size() - 1; i < st.size(); --i) {
        if (st[i].find("\"name\":\"") != std::string::npos) {
            vespalib::asciistream as;
            as << "'\n";
            for (uint32_t j=i-1; j<st.size() - 2; ++j) {
                as << st[j].substr(4) << "\n";
            }
            as << "'";
            return as.str();
        }
    }
    throw vespalib::IllegalArgumentException("Didn't find metric");
}

vespalib::string getJson(MetricManager& mm) {
    vespalib::asciistream as;
    vespalib::JsonStream stream(as, true);
    JsonWriter writer(stream);
    MetricLockGuard guard(mm.getMetricLock());
    mm.visit(guard, mm.getActiveMetrics(guard), writer, "");
    stream.finalize();
    return as.str();
}

}

TEST(ValueMetricTest, test_json)
{
    MetricManager mm;
    DoubleValueMetric m("test", {{"tag"}}, "description");
    mm.registerMetric(mm.getMetricLock(), m);

    vespalib::string expected("'\n"
        "{\n"
        "  \"name\":\"test\",\n"
        "  \"description\":\"description\",\n"
        "  \"values\":\n"
        "  {\n"
        "    \"average\":0.0,\n"
        "    \"sum\":0.0,\n"
        "    \"count\":0,\n"
        "    \"min\":0.0,\n"
        "    \"max\":0.0,\n"
        "    \"last\":0.0\n"
        "  },\n"
        "  \"dimensions\":\n"
        "  {\n"
        "  }\n"
        "}\n'"
    );
    EXPECT_EQ(expected, extractMetricJson(getJson(mm)));
    m.addValue(100);
    expected = "'\n"
        "{\n"
        "  \"name\":\"test\",\n"
        "  \"description\":\"description\",\n"
        "  \"values\":\n"
        "  {\n"
        "    \"average\":100.0,\n"
        "    \"sum\":100.0,\n"
        "    \"count\":1,\n"
        "    \"min\":100.0,\n"
        "    \"max\":100.0,\n"
        "    \"last\":100.0\n"
        "  },\n"
        "  \"dimensions\":\n"
        "  {\n"
        "  }\n"
        "}\n'";
    EXPECT_EQ(expected, extractMetricJson(getJson(mm)));
    m.addValue(500);
    expected = "'\n"
        "{\n"
        "  \"name\":\"test\",\n"
        "  \"description\":\"description\",\n"
        "  \"values\":\n"
        "  {\n"
        "    \"average\":300.0,\n"
        "    \"sum\":600.0,\n"
        "    \"count\":2,\n"
        "    \"min\":100.0,\n"
        "    \"max\":500.0,\n"
        "    \"last\":500.0\n"
        "  },\n"
        "  \"dimensions\":\n"
        "  {\n"
        "  }\n"
        "}\n'";
    EXPECT_EQ(expected, extractMetricJson(getJson(mm)));
}

}
