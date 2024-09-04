// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_usage_filter_test");

using proton::AttributeUsageFilter;
using proton::AttributeUsageStats;
using proton::IAttributeUsageListener;
using search::AddressSpaceComponents;
using search::AddressSpaceUsage;
using vespalib::AddressSpace;

namespace
{

vespalib::AddressSpace enumStoreOverLoad(30_Gi, 0, 32_Gi);

vespalib::AddressSpace multiValueOverLoad(127_Mi, 0, 128_Mi);


class MyAttributeStats : public AttributeUsageStats
{
public:
    void triggerEnumStoreLimit() {
        AddressSpaceUsage usage;
        usage.set(AddressSpaceComponents::enum_store, enumStoreOverLoad);
        merge(usage, "enumeratedName", "ready");
    }

    void triggerMultiValueLimit() {
        AddressSpaceUsage usage;
        usage.set(AddressSpaceComponents::multi_value, multiValueOverLoad);
        merge(usage, "multiValueName", "ready");
    }
};

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

    void testWrite(const std::string &exp) {
        if (exp.empty()) {
            EXPECT_TRUE(filter.acceptWriteOperation());
            State state = filter.getAcceptState();
            EXPECT_TRUE(state.acceptWriteOperation());
            EXPECT_EQ(exp, state.message());
        } else {
            EXPECT_FALSE(filter.acceptWriteOperation());
            State state = filter.getAcceptState();
            EXPECT_FALSE(state.acceptWriteOperation());
            EXPECT_EQ(exp, state.message());
        }
    }

    void setAttributeStats(const AttributeUsageStats &stats) {
        filter.setAttributeStats(stats);
    }
};

AttributeUsageFilterTest::AttributeUsageFilterTest()
    : filter(),
      listener()
{
    auto my_listener = std::make_unique<MyListener>();
    listener = my_listener.get();
    filter.set_listener(std::move(my_listener));
}

AttributeUsageFilterTest::~AttributeUsageFilterTest() = default;

}

TEST_F(AttributeUsageFilterTest, Check_that_default_filter_allows_write)
{
    testWrite("");
}


TEST_F(AttributeUsageFilterTest, Check_that_enum_store_limit_can_be_reached)
{
    filter.setConfig(Config(0.8));
    MyAttributeStats stats;
    stats.triggerEnumStoreLimit();
    setAttributeStats(stats);
    testWrite("addressSpaceLimitReached: { "
                "action: \""
                "add more content nodes"
                "\", "
                "reason: \""
                "max address space in attribute vector components used (0.9375) > limit (0.8)"
                "\", "
                "addressSpace: { used: 32212254720, dead: 0, limit: 34359738368}, "
                "attributeName: \"enumeratedName\", componentName: \"enum-store\", subdb: \"ready\"}");
}

TEST_F(AttributeUsageFilterTest, Check_that_multivalue_limit_can_be_reached)
{
    filter.setConfig(Config(0.8));
    MyAttributeStats stats;
    stats.triggerMultiValueLimit();
    setAttributeStats(stats);
    testWrite("addressSpaceLimitReached: { "
                "action: \""
                "add more content nodes"
                "\", "
                "reason: \""
                "max address space in attribute vector components used (0.992188) > limit (0.8)"
                "\", "
                "addressSpace: { used: 133169152, dead: 0, limit: 134217728}, "
                "attributeName: \"multiValueName\", componentName: \"multi-value\", subdb: \"ready\"}");
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
