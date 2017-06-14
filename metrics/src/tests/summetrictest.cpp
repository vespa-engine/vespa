// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/metrics/metrics.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace metrics {

struct SumMetricTest : public CppUnit::TestFixture {
    void testLongCountMetric();
    void testAverageMetric();
    void testMetricSet();
    void testRemove();
    void testStartValue();

    CPPUNIT_TEST_SUITE(SumMetricTest);
    CPPUNIT_TEST(testLongCountMetric);
    CPPUNIT_TEST(testAverageMetric);
    CPPUNIT_TEST(testMetricSet);
    CPPUNIT_TEST(testRemove);
    CPPUNIT_TEST(testStartValue);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SumMetricTest);

void
SumMetricTest::testLongCountMetric()
{
    MetricSet parent("parent", "", "");
    SumMetric<LongCountMetric> sum("foo", "", "foodesc", &parent);

    LongCountMetric v1("ff", "", "", &parent);
    LongCountMetric v2("aa", "", "", &parent);

    sum.addMetricToSum(v1);
    sum.addMetricToSum(v2);

        // Give them some values
    v1.inc(3);
    v2.inc(7);

        // Verify XML output. Should be in register order.
    std::string expected("foo count=10");
    CPPUNIT_ASSERT_EQUAL(expected, sum.toString());
    CPPUNIT_ASSERT_EQUAL(int64_t(10), sum.getLongValue("value"));
}

void
SumMetricTest::testAverageMetric() {
    MetricSet parent("parent", "", "");
    SumMetric<LongAverageMetric> sum("foo", "", "foodesc", &parent);

    LongAverageMetric v1("ff", "", "", &parent);
    LongAverageMetric v2("aa", "", "", &parent);

    sum.addMetricToSum(v1);
    sum.addMetricToSum(v2);

        // Give them some values
    v1.addValue(3);
    v2.addValue(7);

        // Verify XML output. Should be in register order.
    std::string expected("foo average=5 last=7 min=3 max=7 count=2 total=10");
    CPPUNIT_ASSERT_EQUAL(expected, sum.toString());
    CPPUNIT_ASSERT_EQUAL(int64_t(5), sum.getLongValue("value"));
    CPPUNIT_ASSERT_EQUAL(int64_t(3), sum.getLongValue("min"));
    CPPUNIT_ASSERT_EQUAL(int64_t(7), sum.getLongValue("max"));
}

void
SumMetricTest::testMetricSet() {
    MetricSet parent("parent", "", "");
    SumMetric<MetricSet> sum("foo", "", "bar", &parent);

    MetricSet set1("a", "", "", &parent);
    MetricSet set2("b", "", "", &parent);
    LongValueMetric v1("c", "", "", &set1);
    LongValueMetric v2("d", "", "", &set2);
    LongCountMetric v3("e", "", "", &set1);
    LongCountMetric v4("f", "", "", &set2);

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
    CPPUNIT_ASSERT_EQUAL(expected, "'\n" + sum.toString() + "'");
}

void
SumMetricTest::testRemove()
{
    MetricSet parent("parent", "", "");
    SumMetric<LongCountMetric> sum("foo", "", "foodesc", &parent);

    LongCountMetric v1("ff", "", "", &parent);
    LongCountMetric v2("aa", "", "", &parent);
    LongCountMetric v3("zz", "", "", &parent);

    sum.addMetricToSum(v1);
    sum.addMetricToSum(v2);
    sum.addMetricToSum(v3);

    // Give them some values
    v1.inc(3);
    v2.inc(7);
    v3.inc(10);

    CPPUNIT_ASSERT_EQUAL(int64_t(20), sum.getLongValue("value"));
    sum.removeMetricFromSum(v2);
    CPPUNIT_ASSERT_EQUAL(int64_t(13), sum.getLongValue("value"));
}

void
SumMetricTest::testStartValue()
{
    MetricSnapshot snapshot("active");
    SumMetric<LongValueMetric> sum("foo", "", "foodesc",
                                   &snapshot.getMetrics());
    LongValueMetric start("start", "", "", 0);
    start.set(50);
    sum.setStartValue(start);

    // without children
    CPPUNIT_ASSERT_EQUAL(int64_t(50), sum.getLongValue("value"));

    MetricSnapshot copy("copy");
    copy.recreateSnapshot(snapshot.getMetrics(), true);
    snapshot.addToSnapshot(copy, 100);

    LongValueMetric value("value", "", "", &snapshot.getMetrics());
    sum.addMetricToSum(value);
    value.set(10);

    // with children
    CPPUNIT_ASSERT_EQUAL(int64_t(60), sum.getLongValue("value"));
}

} // metrics
