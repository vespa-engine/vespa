// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/objects/floatingpointtype.h>
#include <vespa/metrics/countmetric.h>

using vespalib::Double;

namespace metrics {

struct CountMetricTest : public CppUnit::TestFixture {
    void testLongCountMetric();

    CPPUNIT_TEST_SUITE(CountMetricTest);
    CPPUNIT_TEST(testLongCountMetric);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(CountMetricTest);

void CountMetricTest::testLongCountMetric()
{
    LongCountMetric m("test", "tag", "description");
    m.set(100);
    CPPUNIT_ASSERT_EQUAL(uint64_t(100), m.getValue());
    m.inc(5);
    CPPUNIT_ASSERT_EQUAL(uint64_t(105), m.getValue());
    m.dec(15);
    CPPUNIT_ASSERT_EQUAL(uint64_t(90), m.getValue());
    LongCountMetric m2(m);
    CPPUNIT_ASSERT_EQUAL(uint64_t(90), m2.getValue());
    m.reset();
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), m.getValue());

    LongCountMetric n("m2", "", "desc");
    n.set(6);
    CPPUNIT_ASSERT_EQUAL(uint64_t(6), n.getValue());

    LongCountMetric o = m2 + n;
    CPPUNIT_ASSERT_EQUAL(uint64_t(96), o.getValue());

    o = m2 - n;
    CPPUNIT_ASSERT_EQUAL(uint64_t(84), o.getValue());

    std::string expected("test count=84");
    CPPUNIT_ASSERT_EQUAL(expected, o.toString());

    CPPUNIT_ASSERT_EQUAL(Double(84), Double(o.getDoubleValue("value")));
    CPPUNIT_ASSERT_EQUAL(int64_t(84), o.getLongValue("value"));
//    (void) expected;
}

}
