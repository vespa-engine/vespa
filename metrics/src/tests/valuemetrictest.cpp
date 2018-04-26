// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/objects/floatingpointtype.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/metrics/jsonwriter.h>
#include <vespa/metrics/metricmanager.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>


using vespalib::Double;

namespace metrics {

struct ValueMetricTest : public CppUnit::TestFixture {
    void testDoubleValueMetric();
    void testDoubleValueMetricNotUpdatedOnNaN();
    void testDoubleValueMetricNotUpdatedOnInfinity();
    void testLongValueMetric();
    void testSmallAverage();
    void testAddValueBatch();
    void testJson();

    CPPUNIT_TEST_SUITE(ValueMetricTest);
    CPPUNIT_TEST(testDoubleValueMetric);
    CPPUNIT_TEST(testDoubleValueMetricNotUpdatedOnNaN);
    CPPUNIT_TEST(testDoubleValueMetricNotUpdatedOnInfinity);
    CPPUNIT_TEST(testLongValueMetric);
    CPPUNIT_TEST(testSmallAverage);
    CPPUNIT_TEST(testAddValueBatch);
    CPPUNIT_TEST(testJson);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ValueMetricTest);

#define ASSERT_AVERAGE(metric, avg, min, max, count, last) \
    CPPUNIT_ASSERT_EQUAL_MSG("avg", Double(avg), Double(metric.getAverage())); \
    CPPUNIT_ASSERT_EQUAL_MSG("cnt", Double(count), Double(metric.getCount())); \
    CPPUNIT_ASSERT_EQUAL_MSG("last", Double(last), Double(metric.getLast())); \
    if (metric.getCount() > 0) { \
    CPPUNIT_ASSERT_EQUAL_MSG("min", Double(min), Double(metric.getMinimum())); \
    CPPUNIT_ASSERT_EQUAL_MSG("max", Double(max), Double(metric.getMaximum())); \
    }

void ValueMetricTest::testDoubleValueMetric()
{
    DoubleValueMetric m("test", "tag", "description");
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

    DoubleValueMetric n("m2", "", "desc");
    n.addValue(60);
    ASSERT_AVERAGE(n, 60, 60, 60, 1, 60);

    DoubleValueMetric o = m2 + n;
    ASSERT_AVERAGE(o, 140, 40, 100, 4, 100);

    o = n + m2;
    ASSERT_AVERAGE(o, 140, 40, 100, 4, 100);

    std::string expected(
            "test average=80 last=40 min=40 max=100 count=3 total=240");
    CPPUNIT_ASSERT_EQUAL(expected, m2.toString());
    expected = "m2 average=140 last=100";
    CPPUNIT_ASSERT_EQUAL(expected, o.toString());

    CPPUNIT_ASSERT_EQUAL(Double(40), Double(m2.getDoubleValue("value")));
    CPPUNIT_ASSERT_EQUAL(Double(80), Double(m2.getDoubleValue("average")));
    CPPUNIT_ASSERT_EQUAL(Double(40), Double(m2.getDoubleValue("min")));
    CPPUNIT_ASSERT_EQUAL(Double(100), Double(m2.getDoubleValue("max")));
    CPPUNIT_ASSERT_EQUAL(Double(40), Double(m2.getDoubleValue("last")));
    CPPUNIT_ASSERT_EQUAL(Double(3), Double(m2.getDoubleValue("count")));
    CPPUNIT_ASSERT_EQUAL(Double(240), Double(m2.getDoubleValue("total")));

    CPPUNIT_ASSERT_EQUAL(int64_t(40), m2.getLongValue("value"));
    CPPUNIT_ASSERT_EQUAL(int64_t(80), m2.getLongValue("average"));
    CPPUNIT_ASSERT_EQUAL(int64_t(40), m2.getLongValue("min"));
    CPPUNIT_ASSERT_EQUAL(int64_t(100), m2.getLongValue("max"));
    CPPUNIT_ASSERT_EQUAL(int64_t(40), m2.getLongValue("last"));
    CPPUNIT_ASSERT_EQUAL(int64_t(3), m2.getLongValue("count"));
    CPPUNIT_ASSERT_EQUAL(int64_t(240), m2.getLongValue("total"));
}

void
ValueMetricTest::testDoubleValueMetricNotUpdatedOnNaN()
{
    DoubleValueMetric m("test", "tag", "description");
    m.addValue(std::numeric_limits<double>::quiet_NaN());
    CPPUNIT_ASSERT_EQUAL(std::string(), m.toString());

    m.addAvgValueWithCount(std::numeric_limits<double>::quiet_NaN(), 123);
    CPPUNIT_ASSERT_EQUAL(std::string(), m.toString());

    m.inc(std::numeric_limits<double>::quiet_NaN());
    CPPUNIT_ASSERT_EQUAL(std::string(), m.toString());

    m.dec(std::numeric_limits<double>::quiet_NaN());
    CPPUNIT_ASSERT_EQUAL(std::string(), m.toString());
}

void
ValueMetricTest::testDoubleValueMetricNotUpdatedOnInfinity()
{
    DoubleValueMetric m("test", "tag", "description");
    m.addValue(std::numeric_limits<double>::infinity());
    CPPUNIT_ASSERT_EQUAL(std::string(), m.toString());

    m.addAvgValueWithCount(std::numeric_limits<double>::quiet_NaN(), 123);
    CPPUNIT_ASSERT_EQUAL(std::string(), m.toString());

    m.inc(std::numeric_limits<double>::infinity());
    CPPUNIT_ASSERT_EQUAL(std::string(), m.toString());

    m.dec(std::numeric_limits<double>::infinity());
    CPPUNIT_ASSERT_EQUAL(std::string(), m.toString());
}

void ValueMetricTest::testLongValueMetric()
{
    LongValueMetric m("test", "tag", "description");
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

    LongValueMetric n("m2", "", "desc");
    n.addValue(60);
    ASSERT_AVERAGE(n, 60, 60, 60, 1, 60);

    LongValueMetric o = m2 + n;
    ASSERT_AVERAGE(o, 140.25, 41, 100, 4, 101);

    o = n + m2;
    ASSERT_AVERAGE(o, 140.25, 41, 100, 4, 101);

    std::string expected(
            "test average=80.3333 last=41 min=41 max=100 count=3 total=241");
    CPPUNIT_ASSERT_EQUAL(expected, m2.toString());
    expected = "m2 average=140.25 last=101";
    CPPUNIT_ASSERT_EQUAL(expected, o.toString());

    CPPUNIT_ASSERT_EQUAL(Double(41), Double(m2.getDoubleValue("value")));
    CPPUNIT_ASSERT_EQUAL(Double(241.0/3), Double(m2.getDoubleValue("average")));
    CPPUNIT_ASSERT_EQUAL(Double(41), Double(m2.getDoubleValue("min")));
    CPPUNIT_ASSERT_EQUAL(Double(100), Double(m2.getDoubleValue("max")));
    CPPUNIT_ASSERT_EQUAL(Double(41), Double(m2.getDoubleValue("last")));
    CPPUNIT_ASSERT_EQUAL(Double(3), Double(m2.getDoubleValue("count")));
    CPPUNIT_ASSERT_EQUAL(Double(241), Double(m2.getDoubleValue("total")));

    CPPUNIT_ASSERT_EQUAL(int64_t(41), m2.getLongValue("value"));
    CPPUNIT_ASSERT_EQUAL(int64_t(80), m2.getLongValue("average"));
    CPPUNIT_ASSERT_EQUAL(int64_t(41), m2.getLongValue("min"));
    CPPUNIT_ASSERT_EQUAL(int64_t(100), m2.getLongValue("max"));
    CPPUNIT_ASSERT_EQUAL(int64_t(41), m2.getLongValue("last"));
    CPPUNIT_ASSERT_EQUAL(int64_t(3), m2.getLongValue("count"));
    CPPUNIT_ASSERT_EQUAL(int64_t(241), m2.getLongValue("total"));
}

void ValueMetricTest::testSmallAverage()
{
    DoubleValueMetric m("test", "tag", "description");
    m.addValue(0.0001);
    m.addValue(0.0002);
    m.addValue(0.0003);
    std::vector<Metric::UP> ownerList;
    Metric::UP c(m.clone(ownerList, Metric::INACTIVE, 0, false));
    std::string expect("test average=0.0002 last=0.0003 min=0.0001 max=0.0003 count=3 total=0.0006");
    CPPUNIT_ASSERT_EQUAL(expect, m.toString());
    CPPUNIT_ASSERT_EQUAL(expect, c->toString());
}

void ValueMetricTest::testAddValueBatch() {
    DoubleValueMetric m("test", "tag", "description");
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

void ValueMetricTest::testJson() {
    MetricManager mm;
    DoubleValueMetric m("test", "tag", "description");
    mm.registerMetric(mm.getMetricLock(), m);

    vespalib::string expected("'\n"
        "{\n"
        "  \"name\":\"test\",\n"
        "  \"description\":\"description\",\n"
        "  \"values\":\n"
        "  {\n"
        "    \"average\":0.0,\n"
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
    CPPUNIT_ASSERT_EQUAL(expected, extractMetricJson(getJson(mm)));
    m.addValue(100);
    expected = "'\n"
        "{\n"
        "  \"name\":\"test\",\n"
        "  \"description\":\"description\",\n"
        "  \"values\":\n"
        "  {\n"
        "    \"average\":100.0,\n"
        "    \"count\":1,\n"
        "    \"min\":100.0,\n"
        "    \"max\":100.0,\n"
        "    \"last\":100.0\n"
        "  },\n"
        "  \"dimensions\":\n"
        "  {\n"
        "  }\n"
        "}\n'";
    CPPUNIT_ASSERT_EQUAL(expected, extractMetricJson(getJson(mm)));
    m.addValue(500);
    expected = "'\n"
        "{\n"
        "  \"name\":\"test\",\n"
        "  \"description\":\"description\",\n"
        "  \"values\":\n"
        "  {\n"
        "    \"average\":300.0,\n"
        "    \"count\":2,\n"
        "    \"min\":100.0,\n"
        "    \"max\":500.0,\n"
        "    \"last\":500.0\n"
        "  },\n"
        "  \"dimensions\":\n"
        "  {\n"
        "  }\n"
        "}\n'";
    CPPUNIT_ASSERT_EQUAL(expected, extractMetricJson(getJson(mm)));
}

}  // namespace metrics
