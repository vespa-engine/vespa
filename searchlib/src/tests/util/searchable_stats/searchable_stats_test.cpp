// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/util/searchable_stats.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("searchable_stats_test");

using namespace search;

TEST(SearchableStatsTest, stats_can_be_merged)
{
    SearchableStats stats;
    EXPECT_EQ(0u, stats.memoryUsage().allocatedBytes());
    EXPECT_EQ(0u, stats.docsInMemory());
    EXPECT_EQ(0u, stats.sizeOnDisk());
    EXPECT_EQ(0u, stats.fusion_size_on_disk());
    {
        SearchableStats rhs;
        EXPECT_EQ(&rhs.memoryUsage(vespalib::MemoryUsage(100,0,0,0)), &rhs);
        EXPECT_EQ(&rhs.docsInMemory(10), &rhs);
        EXPECT_EQ(&rhs.sizeOnDisk(1000), &rhs);
        EXPECT_EQ(&rhs.fusion_size_on_disk(500), &rhs);
        EXPECT_EQ(&stats.merge(rhs), &stats);
    }
    EXPECT_EQ(100u, stats.memoryUsage().allocatedBytes());
    EXPECT_EQ(10u, stats.docsInMemory());
    EXPECT_EQ(1000u, stats.sizeOnDisk());
    EXPECT_EQ(500u, stats.fusion_size_on_disk());

    stats.merge(SearchableStats()
                        .memoryUsage(vespalib::MemoryUsage(150,0,0,0))
                        .docsInMemory(15)
                        .sizeOnDisk(1500)
                        .fusion_size_on_disk(800));
    EXPECT_EQ(250u, stats.memoryUsage().allocatedBytes());
    EXPECT_EQ(25u, stats.docsInMemory());
    EXPECT_EQ(2500u, stats.sizeOnDisk());
    EXPECT_EQ(1300u, stats.fusion_size_on_disk());
}

TEST(SearchableStatsTest, field_stats_can_be_merged)
{
    SearchableStats base_stats;
    base_stats.add_field_stats("f1", FieldIndexStats().memory_usage({100, 40, 10, 5}).size_on_disk(1000)).
        add_field_stats("f2", FieldIndexStats().memory_usage({400, 200, 60, 10}).size_on_disk(1500));
    SearchableStats added_stats;
    added_stats.add_field_stats("f2", FieldIndexStats().memory_usage({300, 100, 40, 5}).size_on_disk(500)).
        add_field_stats("f3", FieldIndexStats().memory_usage({110, 50, 20, 12}).size_on_disk(500));
    SearchableStats act_stats = base_stats;
    act_stats.merge(added_stats);
    SearchableStats exp_stats;
    exp_stats.add_field_stats("f1", FieldIndexStats().memory_usage({100, 40, 10, 5}).size_on_disk(1000)).
        add_field_stats("f2", FieldIndexStats().memory_usage({700, 300, 100, 15}).size_on_disk(2000)).
        add_field_stats("f3", FieldIndexStats().memory_usage({110, 50, 20, 12}).size_on_disk(500));
    EXPECT_EQ(exp_stats, act_stats);
}

GTEST_MAIN_RUN_ALL_TESTS()
