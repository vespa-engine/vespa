// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/attribute/attribute_usage_stats.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::AddressSpaceUsageStats;
using proton::AttributeUsageStats;
using search::AddressSpaceUsage;
using vespalib::AddressSpace;

void
expect_max_usage(size_t used, const vespalib::string& attr_name,
                 const vespalib::string& comp_name, const vespalib::string& sub_name,
                 const AttributeUsageStats& stats)
{
    const auto& max = stats.max_address_space_usage();
    EXPECT_EQ(used, max.getUsage().used());
    EXPECT_EQ(attr_name, max.getAttributeName());
    EXPECT_EQ(comp_name, max.get_component_name());
    EXPECT_EQ(sub_name, max.getSubDbName());
}

TEST(AttributeUsageStatsTest, tracks_max_address_space_usage)
{
    AttributeUsageStats stats;
    {
        AddressSpaceUsage usage;
        usage.set("comp1", AddressSpace(2, 0, 10));
        usage.set("comp2", AddressSpace(3, 0, 10));
        stats.merge(usage, "attr1", "sub1");
        expect_max_usage(3, "attr1", "comp2", "sub1", stats);
    }
    {
        AddressSpaceUsage usage;
        usage.set("comp3", AddressSpace(5, 0, 10));
        usage.set("comp4", AddressSpace(4, 0, 10));
        stats.merge(usage, "attr2", "sub2");
        expect_max_usage(5, "attr2", "comp3", "sub2", stats);
    }
    {
        AddressSpaceUsage usage;
        usage.set("comp5", AddressSpace(5, 0, 10));
        stats.merge(usage, "attr3", "sub2");
        expect_max_usage(5, "attr2", "comp3", "sub2", stats);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
