// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/disk_mem_usage_metrics.h>
#include <vespa/searchcore/proton/server/disk_mem_usage_state.h>
#include <vespa/vespalib/gtest/gtest.h>


using proton::DiskMemUsageMetrics;
using proton::DiskMemUsageState;
using proton::ResourceUsageState;

bool
expect_metrics(double disk_usage, double disk_utilization, double transient_disk, double non_transient_disk,
               double memory_usage, double memory_utilization, double transient_memory, double non_transient_memory,
               const DiskMemUsageMetrics &dm_metrics)
{
    bool result = true;
    EXPECT_DOUBLE_EQ(disk_usage, dm_metrics.total_disk_usage()) << (result = false, "");
    EXPECT_DOUBLE_EQ(disk_utilization, dm_metrics.total_disk_utilization()) << (result = false, "");
    EXPECT_DOUBLE_EQ(transient_disk, dm_metrics.transient_disk_usage()) << (result = false, "");
    EXPECT_DOUBLE_EQ(non_transient_disk, dm_metrics.non_transient_disk_usage()) << (result = false, "");
    EXPECT_DOUBLE_EQ(memory_usage, dm_metrics.total_memory_usage()) << (result = false, "");
    EXPECT_DOUBLE_EQ(memory_utilization, dm_metrics.total_memory_utilization()) << (result = false, "");
    EXPECT_DOUBLE_EQ(transient_memory, dm_metrics.transient_memory_usage()) << (result = false, "");
    EXPECT_DOUBLE_EQ(non_transient_memory, dm_metrics.non_transient_memory_usage()) << (result = false, "");
    return result;
}

TEST(DiskMemUsageMetricsTest, default_value_is_zero)
{
    DiskMemUsageMetrics dm_metrics;
    EXPECT_TRUE(expect_metrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, dm_metrics));
}

TEST(DiskMemUsageMetricsTest, merging_uses_max)
{
    DiskMemUsageMetrics dm_metrics({ResourceUsageState(0.5, 0.4),
                                    ResourceUsageState(0.5, 0.3), 0.1, 0.05});
    EXPECT_TRUE(expect_metrics(0.4, 0.8, 0.1, 0.3,
                               0.3, 0.6, 0.05, 0.25, dm_metrics));
    dm_metrics.merge({ResourceUsageState(0.4, 0.4),
                      ResourceUsageState(0.3, 0.3), 0.1, 0.05});
    EXPECT_TRUE(expect_metrics(0.4, 1.0, 0.1, 0.3,
                               0.3, 1.0, 0.05, 0.25, dm_metrics));
    dm_metrics.merge({ResourceUsageState(0.5, 0.45),
                      ResourceUsageState(0.5, 0.35), 0.1, 0.05});
    EXPECT_TRUE(expect_metrics(0.45, 1.0, 0.1, 0.35,
                               0.35, 1.0, 0.05, 0.3, dm_metrics));
    dm_metrics.merge({ResourceUsageState(0.5, 0.4),
                      ResourceUsageState(0.5, 0.3), 0.15, 0.1});
    EXPECT_TRUE(expect_metrics(0.45, 1.0, 0.15, 0.35,
                               0.35, 1.0, 0.10, 0.3, dm_metrics));
}

GTEST_MAIN_RUN_ALL_TESTS()
