// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/distribution/group.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace storage::lib;

Group::UP
make_group(uint16_t index, vespalib::stringref distribution, uint16_t redundancy = 1)
{
    return std::make_unique<Group>(index, "group", Group::Distribution(distribution), redundancy);
}

TEST(GroupTest, test_operators)
{
    {
        auto g0 = make_group(0, "1|*|*");
        auto g1 = make_group(0, "1|*|*");
        EXPECT_EQ(*g0, *g1);
    }
    {
        auto g0 = make_group(0, "1|*|*");
        auto g1 = make_group(1, "1|*|*");
        EXPECT_FALSE(*g0 == *g1);
    }
    {
        auto g0 = make_group(0, "1|*");
        auto g1 = make_group(0, "1|*|*");
        EXPECT_FALSE(*g0 == *g1);
    }
    {
        auto g0 = make_group(0, "1|*");
        auto g1 = make_group(1, "1|*|*");
        g0->addSubGroup(std::move(g1));
        auto g2 = make_group(0, "1|*");
        EXPECT_FALSE(*g0 == *g2);
    }
}

TEST(GroupTest, test_star_conversion)
{
    {
        auto g = make_group(0, "*", 3);
        const auto& distribution = g->getDistribution(3);
        ASSERT_EQ(1, distribution.size());
        EXPECT_EQ(3, distribution[0]);
    }
    {
        auto g = make_group(0, "1|*|*", 5);
        const auto& distribution = g->getDistribution(5);
        ASSERT_EQ(3, distribution.size());
        EXPECT_EQ(2, distribution[0]);
        EXPECT_EQ(2, distribution[1]);
        EXPECT_EQ(1, distribution[2]);
    }
    {
        auto g = make_group(0, "1|*|*", 3);
        const auto& distribution = g->getDistribution(3);
        ASSERT_EQ(3, distribution.size());
        EXPECT_EQ(1, distribution[0]);
        EXPECT_EQ(1, distribution[1]);
        EXPECT_EQ(1, distribution[2]);
    }
    {
        auto g = make_group(0, "1|*", 3);
        const auto& distribution = g->getDistribution(3);
        ASSERT_EQ(2, distribution.size());
        EXPECT_EQ(2, distribution[0]);
        EXPECT_EQ(1, distribution[1]);
    }
    {
        auto g = make_group(0, "4|*", 3);
        const auto& distribution = g->getDistribution(3);
        ASSERT_EQ(2, distribution.size());
        EXPECT_EQ(2, distribution[0]);
        EXPECT_EQ(1, distribution[1]);
    }
    {
        auto g = make_group(0, "2|*", 3);
        const auto& distribution = g->getDistribution(3);
        ASSERT_EQ(2, distribution.size());
        EXPECT_EQ(2, distribution[0]);
        EXPECT_EQ(1, distribution[1]);
    }
    {
        auto g = make_group(0, "2|*", 0);
        const auto& distribution = g->getDistribution(0);
        ASSERT_EQ(0, distribution.size());
    }
    {
        auto g = make_group(0, "*|*", 3);
        const auto& distribution = g->getDistribution(3);
        ASSERT_EQ(2, distribution.size());
        EXPECT_EQ(2, distribution[0]);
        EXPECT_EQ(1, distribution[1]);
    }
    {
        auto g = make_group(0, "*|*|*", 4);
        const auto& distribution = g->getDistribution(4);
        ASSERT_EQ(3, distribution.size());
        EXPECT_EQ(2, distribution[0]);
        EXPECT_EQ(1, distribution[1]);
        EXPECT_EQ(1, distribution[2]);
    }
    {
        auto g = make_group(0, "*|*|*", 5);
        const auto& distribution = g->getDistribution(5);
        ASSERT_EQ(3, distribution.size());
        EXPECT_EQ(2, distribution[0]);
        EXPECT_EQ(2, distribution[1]);
        EXPECT_EQ(1, distribution[2]);
    }
    {
        auto g = make_group(0, "*|*|*", 12);
        const auto& distribution = g->getDistribution(12); // Shall be evenly divided
        ASSERT_EQ(3, distribution.size());
        EXPECT_EQ(4, distribution[0]);
        EXPECT_EQ(4, distribution[1]);
        EXPECT_EQ(4, distribution[2]);
    }
    {
        auto g = make_group(0, "*|*|*|*", 5);
        const auto& distribution = g->getDistribution(5);
        ASSERT_EQ(4, distribution.size());
        EXPECT_EQ(2, distribution[0]);
        EXPECT_EQ(1, distribution[1]);
        EXPECT_EQ(1, distribution[2]);
        EXPECT_EQ(1, distribution[3]);
    }
}

TEST(GroupTest, test_group_index_order)
{
    uint16_t numGroups=10;

    std::vector<Group::UP> groups;
    for (uint16_t i=0; i< numGroups; ++i){
        groups.push_back(make_group(i, "5|*"));
    }

    for (uint16_t i=1; i< 10; ++i){
        groups[0]->addSubGroup(std::move(groups[numGroups-i]));
    }

    uint16_t last=0;
    for (const auto& sub_group : groups[0]->getSubGroups()) {
        uint16_t index = sub_group.second->getIndex();
        ASSERT_EQ(index, sub_group.first);
        EXPECT_LE(last, index);
        last = index;
    }
}

