// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/disk_mem_usage_metrics.h>
#include <vespa/searchcore/proton/server/resource_usage_state.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::DiskMemUsageMetrics;
using proton::ResourceUsageState;
using proton::ResourceUsageWithLimit;

void expect_metrics(double disk_usage, double disk_utilization, double transient_disk, double non_transient_disk,
                    double reserved_disk_space, double non_transient_disk_usage_and_reserved_disk_space,
                    double reported_disk_usage, double memory_usage, double memory_utilization,
                    double transient_memory, double non_transient_memory, double reserved_memory,
                    double non_transient_memory_and_reserved_memory, double reported_memory_usage,
                    const DiskMemUsageMetrics& dm_metrics) {
    EXPECT_DOUBLE_EQ(disk_usage, dm_metrics.total_disk_usage());
    EXPECT_DOUBLE_EQ(disk_utilization, dm_metrics.total_disk_utilization());
    EXPECT_DOUBLE_EQ(transient_disk, dm_metrics.transient_disk_usage());
    EXPECT_DOUBLE_EQ(non_transient_disk, dm_metrics.non_transient_disk_usage());
    EXPECT_DOUBLE_EQ(reserved_disk_space, dm_metrics.reserved_disk_space());
    EXPECT_DOUBLE_EQ(non_transient_disk_usage_and_reserved_disk_space,
                     dm_metrics.non_transient_disk_usage_and_reserved_disk_space());
    EXPECT_DOUBLE_EQ(reported_disk_usage, dm_metrics.reported_disk_usage());
    EXPECT_DOUBLE_EQ(memory_usage, dm_metrics.total_memory_usage());
    EXPECT_DOUBLE_EQ(memory_utilization, dm_metrics.total_memory_utilization());
    EXPECT_DOUBLE_EQ(transient_memory, dm_metrics.transient_memory_usage());
    EXPECT_DOUBLE_EQ(non_transient_memory, dm_metrics.non_transient_memory_usage());
    EXPECT_DOUBLE_EQ(reserved_memory, dm_metrics.reserved_memory());
    EXPECT_DOUBLE_EQ(non_transient_memory_and_reserved_memory,
                     dm_metrics.non_transient_memory_usage_and_reserved_memory());
    EXPECT_DOUBLE_EQ(reported_memory_usage, dm_metrics.reported_memory_usage());
}

TEST(DiskMemUsageMetricsTest, default_value_is_zero) {
    DiskMemUsageMetrics dm_metrics;
    expect_metrics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, dm_metrics);
}

TEST(DiskMemUsageMetricsTest, merging_uses_max) {
    DiskMemUsageMetrics dm_metrics({ResourceUsageWithLimit(0.4, 0.5), ResourceUsageWithLimit(0.3, 0.5), 0.3, 0.25,
                                    0.02, 0.5, 0.03, 0.5, 0.1, 0.05});
    {
        SCOPED_TRACE("first");
        expect_metrics(0.4, 0.8, 0.1, 0.3, 0.02, 0.32, 0.31, 0.3, 0.6, 0.05, 0.25, 0.03, 0.28, 0.265, dm_metrics);
    }
    dm_metrics.merge({ResourceUsageWithLimit(0.4, 0.4), ResourceUsageWithLimit(0.3, 0.3), 0.3, 0.25, 0.04, 0.5, 0.06,
                      0.5, 0.1, 0.05});
    {
        SCOPED_TRACE("reserved bump");
        expect_metrics(0.4, 1.0, 0.1, 0.3, 0.04, 0.34, 0.32, 0.3, 1.0, 0.05, 0.25, 0.06, 0.31, 0.28, dm_metrics);
    }
    dm_metrics.merge({ResourceUsageWithLimit(0.45, 0.5), ResourceUsageWithLimit(0.35, 0.5), 0.35, 0.3, 0.03, 0.5,
                      0.01, 0.5, 0.1, 0.05});
    {
        SCOPED_TRACE("disk usage bump");
        expect_metrics(0.45, 1.0, 0.1, 0.35, 0.04, 0.38, 0.365, 0.35, 1.0, 0.05, 0.3, 0.06, 0.31, 0.305, dm_metrics);
    }
    dm_metrics.merge({ResourceUsageWithLimit(0.4, 0.5), ResourceUsageWithLimit(0.3, 0.5), 0.25, 0.2, 0.0, 0.5, 0.01,
                      0.5, 0.15, 0.1});
    {
        SCOPED_TRACE("after disk usage bump");
        expect_metrics(0.45, 1.0, 0.15, 0.35, 0.04, 0.38, 0.365, 0.35, 1.0, 0.10, 0.3, 0.06, 0.31, 0.305, dm_metrics);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
