// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/server/disk_mem_usage_filter.h>
#include <vespa/searchcore/proton/server/resource_usage_state.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace proton;

namespace fs = std::filesystem;

struct DiskMemUsageFilterTest : public ::testing::Test
{
    DiskMemUsageFilter _filter;
    using State = DiskMemUsageFilter::State;
    using Config = DiskMemUsageFilter::Config;

    DiskMemUsageFilterTest()
        : _filter(HwInfo(HwInfo::Disk(100, false, false), HwInfo::Memory(1000), HwInfo::Cpu(0)))
    {
        _filter.set_resource_usage(TransientResourceUsage(), vespalib::ProcessMemoryStats(297, 298, 299, 300, 42), 20);
    }

    void testWrite(const vespalib::string &exp) {
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
        _filter.set_resource_usage(_filter.get_transient_resource_usage(), _filter.getMemoryStats(), 90);
    }

    void triggerMemoryLimit()
    {
        _filter.set_resource_usage(TransientResourceUsage(), vespalib::ProcessMemoryStats(897, 898, 899, 900, 43), _filter.getDiskUsedSize());
    }
};

TEST_F(DiskMemUsageFilterTest, default_filter_allows_write)
{
    testWrite("");
}

TEST_F(DiskMemUsageFilterTest, stats_are_wired_through)
{
    EXPECT_EQ(42u, _filter.getMemoryStats().getMappingsCount());
    triggerMemoryLimit();
    EXPECT_EQ(43u, _filter.getMemoryStats().getMappingsCount());
}

void
assertResourceUsage(double usage, double limit, double utilization, const ResourceUsageState &state)
{
    EXPECT_EQ(usage, state.usage());
    EXPECT_EQ(limit, state.limit());
    EXPECT_DOUBLE_EQ(utilization, state.utilization());
}

TEST_F(DiskMemUsageFilterTest, disk_limit_can_be_reached)
{
    _filter.setConfig(Config(1.0, 0.8));
    assertResourceUsage(0.2, 0.8, 0.25, _filter.usageState().diskState());
    triggerDiskLimit();
    testWrite("diskLimitReached: { "
              "action: \"add more content nodes\", "
              "reason: \"disk used (0.9) > disk limit (0.8)\", "
              "stats: { "
              "capacity: 100, used: 90, diskUsed: 0.9, diskLimit: 0.8}}");
    assertResourceUsage(0.9, 0.8, 1.125, _filter.usageState().diskState());
}

TEST_F(DiskMemUsageFilterTest, memory_limit_can_be_reached)
{
    _filter.setConfig(Config(0.8, 1.0));
    assertResourceUsage(0.3, 0.8, 0.375, _filter.usageState().memoryState());
    triggerMemoryLimit();
    testWrite("memoryLimitReached: { "
              "action: \"add more content nodes\", "
              "reason: \"memory used (0.9) > memory limit (0.8)\", "
              "stats: { "
              "mapped: { virt: 897, rss: 898}, "
              "anonymous: { virt: 899, rss: 900}, "
              "physicalMemory: 1000, memoryUsed: 0.9, memoryLimit: 0.8}}");
    assertResourceUsage(0.9, 0.8, 1.125, _filter.usageState().memoryState());
}

TEST_F(DiskMemUsageFilterTest, both_disk_limit_and_memory_limit_can_be_reached)
{
    _filter.setConfig(Config(0.8, 0.8));
    triggerMemoryLimit();
    triggerDiskLimit();
    testWrite("memoryLimitReached: { "
              "action: \"add more content nodes\", "
              "reason: \"memory used (0.9) > memory limit (0.8)\", "
              "stats: { "
              "mapped: { virt: 897, rss: 898}, "
              "anonymous: { virt: 899, rss: 900}, "
              "physicalMemory: 1000, memoryUsed: 0.9, memoryLimit: 0.8}}, "
              "diskLimitReached: { "
              "action: \"add more content nodes\", "
              "reason: \"disk used (0.9) > disk limit (0.8)\", "
              "stats: { "
              "capacity: 100, used: 90, diskUsed: 0.9, diskLimit: 0.8}}");
}

TEST_F(DiskMemUsageFilterTest, transient_and_non_transient_disk_usage_tracked_in_usage_state_and_metrics)
{
    _filter.set_resource_usage({15, 0}, _filter.getMemoryStats(), _filter.getDiskUsedSize());
    EXPECT_DOUBLE_EQ(0.15, _filter.usageState().transient_disk_usage());
    EXPECT_DOUBLE_EQ(0.15, _filter.get_metrics().transient_disk_usage());
    EXPECT_DOUBLE_EQ(0.05, _filter.usageState().non_transient_disk_usage());
    EXPECT_DOUBLE_EQ(0.05, _filter.get_metrics().non_transient_disk_usage());
}

TEST_F(DiskMemUsageFilterTest, transient_and_non_transient_memory_usage_tracked_in_usage_state_and_metrics)
{
    _filter.set_resource_usage({0, 100}, _filter.getMemoryStats(), _filter.getDiskUsedSize());
    EXPECT_DOUBLE_EQ(0.1, _filter.usageState().transient_memory_usage());
    EXPECT_DOUBLE_EQ(0.1, _filter.get_metrics().transient_memory_usage());
    EXPECT_DOUBLE_EQ(0.2, _filter.usageState().non_transient_memory_usage());
    EXPECT_DOUBLE_EQ(0.2, _filter.get_metrics().non_transient_memory_usage());
}

GTEST_MAIN_RUN_ALL_TESTS()
