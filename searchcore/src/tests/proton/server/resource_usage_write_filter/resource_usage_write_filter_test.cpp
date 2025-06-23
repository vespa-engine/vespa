// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/resource_usage_write_filter.h>
#include <vespa/searchcore/proton/server/resource_usage_notifier.h>
#include <vespa/searchcore/proton/server/resource_usage_with_limit.h>
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/size_literals.h>

using namespace proton;
using search::AddressSpaceUsage;
using search::AddressSpaceComponents;
using vespalib::HwInfo;

namespace fs = std::filesystem;

namespace {

vespalib::AddressSpace enumStoreOverLoad(30_Gi, 0, 32_Gi);

vespalib::AddressSpace multiValueOverLoad(127_Mi, 0, 128_Mi);


class MyAttributeStats : public AttributeUsageStats
{
public:
    MyAttributeStats()
        : AttributeUsageStats("test")
    {
    }
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

}

struct ResourceUsageWriteFilterTest : public ::testing::Test
{
    ResourceUsageWriteFilter _filter;
    ResourceUsageNotifier _notifier;
    using State = ResourceUsageWriteFilter::State;
    using Config = ResourceUsageNotifier::Config;

    ResourceUsageWriteFilterTest()
        : _filter(HwInfo(HwInfo::Disk(100, false, false), HwInfo::Memory(1000), HwInfo::Cpu(0))),
          _notifier(_filter)
    {
        _notifier.set_resource_usage(TransientResourceUsage(), vespalib::ProcessMemoryStats(297, 298, 300), 20);
    }

    void testWrite(const std::string &exp) {
        if (exp.empty()) {
            EXPECT_TRUE(_filter.acceptWriteOperation());
            State state = _filter.getAcceptState();
            EXPECT_TRUE(state.acceptWriteOperation());
            EXPECT_EQ(exp, state.message());
        } else {
            EXPECT_FALSE(_filter.acceptWriteOperation());
            State state = _filter.getAcceptState();
            EXPECT_FALSE(state.acceptWriteOperation());
            EXPECT_EQ(exp, state.message());
        }
    }

    void triggerDiskLimit() {
        _notifier.set_resource_usage(_notifier.get_transient_resource_usage(), _notifier.getMemoryStats(), 90);
    }

    void triggerMemoryLimit()
    {
        _notifier.set_resource_usage(TransientResourceUsage(), vespalib::ProcessMemoryStats(897, 898, 900), _notifier.getDiskUsedSize());
    }

    void notify_attribute_usage(const AttributeUsageStats& usage) {
        _notifier.notify_attribute_usage(usage);
    }
};

TEST_F(ResourceUsageWriteFilterTest, default_filter_allows_write)
{
    testWrite("");
}

TEST_F(ResourceUsageWriteFilterTest, stats_are_wired_through)
{
    EXPECT_EQ(297u, _notifier.getMemoryStats().getVirt());
    triggerMemoryLimit();
    EXPECT_EQ(897u, _notifier.getMemoryStats().getVirt());
}

void
assertResourceUsage(double usage, double limit, double utilization, const ResourceUsageWithLimit &state)
{
    EXPECT_EQ(usage, state.usage());
    EXPECT_EQ(limit, state.limit());
    EXPECT_DOUBLE_EQ(utilization, state.utilization());
}

TEST_F(ResourceUsageWriteFilterTest, reconfig_with_identical_config_is_noop)
{
    EXPECT_TRUE(_notifier.setConfig(Config(1.0, 0.8, AttributeUsageFilterConfig())));
    assertResourceUsage(0.2, 0.8, 0.25, _notifier.usageState().diskState());
    EXPECT_FALSE(_notifier.setConfig(Config(1.0, 0.8, AttributeUsageFilterConfig())));
    assertResourceUsage(0.2, 0.8, 0.25, _notifier.usageState().diskState());
}

TEST_F(ResourceUsageWriteFilterTest, disk_limit_can_be_reached)
{
    EXPECT_TRUE(_notifier.setConfig(Config(1.0, 0.8, AttributeUsageFilterConfig())));
    assertResourceUsage(0.2, 0.8, 0.25, _notifier.usageState().diskState());
    triggerDiskLimit();
    testWrite("diskLimitReached: { "
              "action: \"add more content nodes\", "
              "reason: \"disk used (0.9) > disk limit (0.8)\", "
              "stats: { "
              "capacity: 100, used: 90, diskUsed: 0.9, diskLimit: 0.8}}");
    assertResourceUsage(0.9, 0.8, 1.125, _notifier.usageState().diskState());
}

TEST_F(ResourceUsageWriteFilterTest, memory_limit_can_be_reached)
{
    EXPECT_TRUE(_notifier.setConfig(Config(0.8, 1.0, AttributeUsageFilterConfig())));
    assertResourceUsage(0.3, 0.8, 0.375, _notifier.usageState().memoryState());
    triggerMemoryLimit();
    testWrite("memoryLimitReached: { "
              "action: \"add more content nodes\", "
              "reason: \"memory used (0.9) > memory limit (0.8)\", "
              "stats: { "
              "virt: 897, "
              "rss: { mapped: 898, anonymous: 900}, "
              "physicalMemory: 1000, memoryUsed: 0.9, memoryLimit: 0.8}}");
    assertResourceUsage(0.9, 0.8, 1.125, _notifier.usageState().memoryState());
}

TEST_F(ResourceUsageWriteFilterTest, both_disk_limit_and_memory_limit_can_be_reached)
{
    EXPECT_TRUE(_notifier.setConfig(Config(0.8, 0.8, AttributeUsageFilterConfig())));
    triggerMemoryLimit();
    triggerDiskLimit();
    testWrite("memoryLimitReached: { "
              "action: \"add more content nodes\", "
              "reason: \"memory used (0.9) > memory limit (0.8)\", "
              "stats: { "
              "virt: 897, "
              "rss: { mapped: 898, anonymous: 900}, "
              "physicalMemory: 1000, memoryUsed: 0.9, memoryLimit: 0.8}}, "
              "diskLimitReached: { "
              "action: \"add more content nodes\", "
              "reason: \"disk used (0.9) > disk limit (0.8)\", "
              "stats: { "
              "capacity: 100, used: 90, diskUsed: 0.9, diskLimit: 0.8}}");
}

TEST_F(ResourceUsageWriteFilterTest, transient_and_non_transient_disk_usage_tracked_in_usage_state_and_metrics)
{
    _notifier.set_resource_usage({15, 0}, _notifier.getMemoryStats(), _notifier.getDiskUsedSize());
    EXPECT_DOUBLE_EQ(0.15, _notifier.usageState().transient_disk_usage());
    EXPECT_DOUBLE_EQ(0.15, _notifier.get_metrics().transient_disk_usage());
    EXPECT_DOUBLE_EQ(0.05, _notifier.usageState().non_transient_disk_usage());
    EXPECT_DOUBLE_EQ(0.05, _notifier.get_metrics().non_transient_disk_usage());
}

TEST_F(ResourceUsageWriteFilterTest, transient_and_non_transient_memory_usage_tracked_in_usage_state_and_metrics)
{
    _notifier.set_resource_usage({0, 100}, _notifier.getMemoryStats(), _notifier.getDiskUsedSize());
    EXPECT_DOUBLE_EQ(0.1, _notifier.usageState().transient_memory_usage());
    EXPECT_DOUBLE_EQ(0.1, _notifier.get_metrics().transient_memory_usage());
    EXPECT_DOUBLE_EQ(0.2, _notifier.usageState().non_transient_memory_usage());
    EXPECT_DOUBLE_EQ(0.2, _notifier.get_metrics().non_transient_memory_usage());
}

TEST_F(ResourceUsageWriteFilterTest, check_that_enum_store_limit_can_be_reached)
{
    EXPECT_TRUE(_notifier.setConfig(Config(0.8, 0.8, AttributeUsageFilterConfig(0.8))));
    MyAttributeStats stats;
    stats.triggerEnumStoreLimit();
    notify_attribute_usage(stats);
    testWrite("addressSpaceLimitReached: { "
              "action: \""
              "add more content nodes"
              "\", "
              "reason: \""
              "max address space in attribute vector components used (0.9375) > limit (0.8)"
              "\", "
              "addressSpace: { used: 32212254720, dead: 0, limit: 34359738368}, "
              "document_type: \"test\", "
              "attributeName: \"enumeratedName\", componentName: \"enum-store\", subdb: \"ready\"}");
}

TEST_F(ResourceUsageWriteFilterTest, Check_that_multivalue_limit_can_be_reached)
{
    EXPECT_TRUE(_notifier.setConfig(Config(0.8, 0.8, AttributeUsageFilterConfig(0.8))));
    MyAttributeStats stats;
    stats.triggerMultiValueLimit();
    notify_attribute_usage(stats);
    testWrite("addressSpaceLimitReached: { "
              "action: \""
              "add more content nodes"
              "\", "
              "reason: \""
              "max address space in attribute vector components used (0.992188) > limit (0.8)"
              "\", "
              "addressSpace: { used: 133169152, dead: 0, limit: 134217728}, "
              "document_type: \"test\", "
              "attributeName: \"multiValueName\", componentName: \"multi-value\", subdb: \"ready\"}");
}

GTEST_MAIN_RUN_ALL_TESTS()
