// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/util/searchable_stats.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("searchable_stats_test");

using namespace search;

TEST(SearchableStatsTest, merge_also_tracks_max_size_on_disk_for_component)
{
    SearchableStats stats;
    EXPECT_EQ(0u, stats.memoryUsage().allocatedBytes());
    EXPECT_EQ(0u, stats.docsInMemory());
    EXPECT_EQ(0u, stats.sizeOnDisk());
    EXPECT_EQ(0u, stats.max_component_size_on_disk());
    {
        SearchableStats rhs;
        EXPECT_EQ(&rhs.memoryUsage(vespalib::MemoryUsage(100,0,0,0)), &rhs);
        EXPECT_EQ(&rhs.docsInMemory(10), &rhs);
        EXPECT_EQ(&rhs.sizeOnDisk(1000), &rhs);
        EXPECT_EQ(1000u, rhs.max_component_size_on_disk());
        EXPECT_EQ(&stats.merge(rhs), &stats);
    }
    EXPECT_EQ(100u, stats.memoryUsage().allocatedBytes());
    EXPECT_EQ(10u, stats.docsInMemory());
    EXPECT_EQ(1000u, stats.sizeOnDisk());
    EXPECT_EQ(1000u, stats.max_component_size_on_disk());

    stats.merge(SearchableStats()
                        .memoryUsage(vespalib::MemoryUsage(150,0,0,0))
                        .docsInMemory(15)
                        .sizeOnDisk(1500));
    EXPECT_EQ(250u, stats.memoryUsage().allocatedBytes());
    EXPECT_EQ(25u, stats.docsInMemory());
    EXPECT_EQ(2500u, stats.sizeOnDisk());
    EXPECT_EQ(1500u, stats.max_component_size_on_disk());

    stats.merge(SearchableStats()
                        .memoryUsage(vespalib::MemoryUsage(120,0,0,0))
                        .docsInMemory(12)
                        .sizeOnDisk(1200));
    EXPECT_EQ(370u, stats.memoryUsage().allocatedBytes());
    EXPECT_EQ(37u, stats.docsInMemory());
    EXPECT_EQ(3700u, stats.sizeOnDisk());
    EXPECT_EQ(1500u, stats.max_component_size_on_disk());
}

GTEST_MAIN_RUN_ALL_TESTS()
