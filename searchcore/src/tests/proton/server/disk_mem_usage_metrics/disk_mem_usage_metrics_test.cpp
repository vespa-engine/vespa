// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/disk_mem_usage_metrics.h>
#include <vespa/searchcore/proton/server/disk_mem_usage_state.h>
#include <vespa/vespalib/gtest/gtest.h>


using proton::DiskMemUsageMetrics;
using proton::DiskMemUsageState;
using proton::ResourceUsageState;

bool
expect_metrics(double disk_usage, double disk_utilization, double memory_usage, double memory_utilization, const DiskMemUsageMetrics &dm_metrics)
{
    bool result = true;
    EXPECT_DOUBLE_EQ(disk_usage, dm_metrics.get_disk_usage()) << (result = false, "");
    EXPECT_DOUBLE_EQ(disk_utilization, dm_metrics.get_disk_utilization()) << (result = false, "");
    EXPECT_DOUBLE_EQ(memory_usage, dm_metrics.get_memory_usage()) << (result = false, "");
    EXPECT_DOUBLE_EQ(memory_utilization, dm_metrics.get_memory_utilization()) << (result = false, "");
    return result;
}

TEST(DiskMemUsageMetricsTest, default_value_is_zero)
{
    DiskMemUsageMetrics dm_metrics;
    EXPECT_TRUE(expect_metrics(0.0, 0.0, 0.0, 0.0, dm_metrics));
}

TEST(DiskMemUsageMetricsTest, merging_uses_max)
{
    DiskMemUsageMetrics dm_metrics({ResourceUsageState(0.5, 0.4), ResourceUsageState(0.5, 0.3)});
    EXPECT_TRUE(expect_metrics(0.4, 0.8, 0.3, 0.6, dm_metrics));
    dm_metrics.merge({ResourceUsageState(0.4, 0.4), ResourceUsageState(0.5, 0.4)});
    EXPECT_TRUE(expect_metrics(0.4, 1.0, 0.4, 0.8, dm_metrics));
    dm_metrics.merge({ResourceUsageState(0.5, 0.4), ResourceUsageState(0.5, 0.3)});
    EXPECT_TRUE(expect_metrics(0.4, 1.0, 0.4, 0.8, dm_metrics));
}

GTEST_MAIN_RUN_ALL_TESTS()
