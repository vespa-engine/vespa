// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/objects/floatingpointtype.h>
#include <vespa/metrics/countmetric.h>
#include <gtest/gtest.h>

using vespalib::Double;

namespace metrics {

TEST(CountMetricTest, testLongCountMetric)
{
    LongCountMetric m("test", {{"tag"}}, "description");
    m.set(100);
    EXPECT_EQ(uint64_t(100), m.getValue());
    m.inc(5);
    EXPECT_EQ(uint64_t(105), m.getValue());
    m.dec(15);
    EXPECT_EQ(uint64_t(90), m.getValue());
    LongCountMetric m2(m);
    EXPECT_EQ(uint64_t(90), m2.getValue());
    m.reset();
    EXPECT_EQ(uint64_t(0), m.getValue());

    LongCountMetric n("m2", {}, "desc");
    n.set(6);
    EXPECT_EQ(uint64_t(6), n.getValue());

    LongCountMetric o = m2 + n;
    EXPECT_EQ(uint64_t(96), o.getValue());

    o = m2 - n;
    EXPECT_EQ(uint64_t(84), o.getValue());

    std::string expected("test count=84");
    EXPECT_EQ(expected, o.toString());

    EXPECT_EQ(Double(84), Double(o.getDoubleValue("value")));
    EXPECT_EQ(int64_t(84), o.getLongValue("value"));
}

}
