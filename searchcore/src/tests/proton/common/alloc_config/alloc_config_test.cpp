// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::AllocConfig;
using proton::AllocStrategy;
using proton::SubDbType;
using search::CompactionStrategy;
using search::GrowStrategy;

namespace {

CompactionStrategy baseline_compaction_strategy(0.2, 0.25);

GrowStrategy make_grow_strategy(uint32_t initial_docs) {
    return GrowStrategy(initial_docs, 0.1, 1, 0.15);
}

AllocStrategy make_alloc_strategy(uint32_t initial_docs) {
    return AllocStrategy(make_grow_strategy(initial_docs), baseline_compaction_strategy, 10000);
}

};

TEST(AllocConfigTest, can_make_allocation_strategy_for_sub_dbs)
{
    AllocConfig config(make_alloc_strategy(10000000), 5, 2);
    EXPECT_EQ(make_alloc_strategy(20000000), config.make_alloc_strategy(SubDbType::READY));
    EXPECT_EQ(make_alloc_strategy(100000),  config.make_alloc_strategy(SubDbType::REMOVED));
    EXPECT_EQ(make_alloc_strategy(30000000), config.make_alloc_strategy(SubDbType::NOTREADY));
}

GTEST_MAIN_RUN_ALL_TESTS()
