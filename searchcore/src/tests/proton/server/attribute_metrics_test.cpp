// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("attribute_metrics_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/proton/metrics/attribute_metrics.h>

using namespace proton;

class Test : public vespalib::TestApp
{
public:
    int Main();
};

int
Test::Main()
{
    TEST_INIT("attribute_metrics_test");
    {
        AttributeMetrics attrMetrics(0);
        EXPECT_EQUAL(0u, attrMetrics.list.release().size());
        {
            AttributeMetrics::List::Entry::LP e1 = attrMetrics.list.add("foo");
            AttributeMetrics::List::Entry::LP e2 = attrMetrics.list.add("bar");
            AttributeMetrics::List::Entry::LP e3 = attrMetrics.list.add("foo");
            EXPECT_TRUE(e1.get() != 0);
            EXPECT_TRUE(e2.get() != 0);
            EXPECT_TRUE(e3.get() == 0);
        }
        {
            const AttributeMetrics &constMetrics = attrMetrics;
            AttributeMetrics::List::Entry::LP e1 = constMetrics.list.get("foo");
            AttributeMetrics::List::Entry::LP e2 = constMetrics.list.get("bar");
            AttributeMetrics::List::Entry::LP e3 = constMetrics.list.get("baz");
            EXPECT_TRUE(e1.get() != 0);
            EXPECT_TRUE(e2.get() != 0);
            EXPECT_TRUE(e3.get() == 0);
        }
        EXPECT_EQUAL(2u, attrMetrics.list.release().size());
        {
            const AttributeMetrics &constMetrics = attrMetrics;
            AttributeMetrics::List::Entry::LP e1 = constMetrics.list.get("foo");
            AttributeMetrics::List::Entry::LP e2 = constMetrics.list.get("bar");
            AttributeMetrics::List::Entry::LP e3 = constMetrics.list.get("baz");
            EXPECT_TRUE(e1.get() == 0);
            EXPECT_TRUE(e2.get() == 0);
            EXPECT_TRUE(e3.get() == 0);
        }
        EXPECT_EQUAL(0u, attrMetrics.list.release().size());
    }
    TEST_DONE();
}

TEST_APPHOOK(Test);
