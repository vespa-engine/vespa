// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("attribute_metrics_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/proton/metrics/legacy_attribute_metrics.h>

using namespace proton;

class Test : public vespalib::TestApp
{
public:
    int Main() override;
};

int
Test::Main()
{
    TEST_INIT("attribute_metrics_test");
    {
        LegacyAttributeMetrics attrMetrics(0);
        EXPECT_EQUAL(0u, attrMetrics.list.release().size());
        {
            LegacyAttributeMetrics::List::Entry *e1 = attrMetrics.list.add("foo");
            LegacyAttributeMetrics::List::Entry *e2 = attrMetrics.list.add("bar");
            LegacyAttributeMetrics::List::Entry *e3 = attrMetrics.list.add("foo");
            EXPECT_TRUE(e1 != nullptr);
            EXPECT_TRUE(e2 != nullptr);
            EXPECT_TRUE(e3 == nullptr);
        }
        {
            const LegacyAttributeMetrics &constMetrics = attrMetrics;
            LegacyAttributeMetrics::List::Entry *e1 = constMetrics.list.get("foo");
            LegacyAttributeMetrics::List::Entry *e2 = constMetrics.list.get("bar");
            LegacyAttributeMetrics::List::Entry *e3 = constMetrics.list.get("baz");
            EXPECT_TRUE(e1 != nullptr);
            EXPECT_TRUE(e2 != nullptr);
            EXPECT_TRUE(e3 == nullptr);
        }
        EXPECT_EQUAL(2u, attrMetrics.list.release().size());
        {
            const LegacyAttributeMetrics &constMetrics = attrMetrics;
            LegacyAttributeMetrics::List::Entry *e1 = constMetrics.list.get("foo");
            LegacyAttributeMetrics::List::Entry *e2 = constMetrics.list.get("bar");
            LegacyAttributeMetrics::List::Entry *e3 = constMetrics.list.get("baz");
            EXPECT_TRUE(e1 == nullptr);
            EXPECT_TRUE(e2 == nullptr);
            EXPECT_TRUE(e3 == nullptr);
        }
        EXPECT_EQUAL(0u, attrMetrics.list.release().size());
    }
    TEST_DONE();
}

TEST_APPHOOK(Test);
