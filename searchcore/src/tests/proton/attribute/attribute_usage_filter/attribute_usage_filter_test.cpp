// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_usage_filter_test");

using proton::AttributeUsageFilter;
using proton::AttributeUsageStats;
using proton::IAttributeUsageListener;
using search::AddressSpaceUsage;
using vespalib::AddressSpace;

namespace
{

class MyListener : public IAttributeUsageListener {
public:
    AttributeUsageStats stats;
    MyListener() : stats() {}
    void notify_attribute_usage(const AttributeUsageStats &stats_in) override {
        stats = stats_in;
    }
};

class AttributeUsageFilterTest : public  ::testing::Test
{
protected:
    AttributeUsageFilter filter;
    const MyListener* listener;
    using State = AttributeUsageFilter::State;
    using Config = AttributeUsageFilter::Config;

    AttributeUsageFilterTest();
    ~AttributeUsageFilterTest() override;

    void setAttributeStats(const AttributeUsageStats &stats) {
        filter.setAttributeStats(stats);
    }
};

AttributeUsageFilterTest::AttributeUsageFilterTest()
    : filter(),
      listener(nullptr)
{
    auto my_listener = std::make_unique<MyListener>();
    listener = my_listener.get();
    filter.set_listener(std::move(my_listener));
}

AttributeUsageFilterTest::~AttributeUsageFilterTest() = default;

}

TEST_F(AttributeUsageFilterTest, listener_is_updated_when_attribute_stats_change)
{
   AttributeUsageStats stats;
   AddressSpaceUsage usage;
   usage.set("my_comp", AddressSpace(12, 10, 15));
   stats.merge(usage, "my_attr", "my_subdb");
   setAttributeStats(stats);
   EXPECT_EQ(stats, listener->stats);
}

GTEST_MAIN_RUN_ALL_TESTS()
