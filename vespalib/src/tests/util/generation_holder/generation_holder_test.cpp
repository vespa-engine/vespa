// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generationholder.h>

using vespalib::GenerationHolder;
using MyHeld = vespalib::GenerationHeldBase;

TEST(GenerationHolderTest, basic_tracking)
{
    GenerationHolder gh;
    gh.hold(std::make_unique<MyHeld>(sizeof(int32_t)));
    gh.transferHoldLists(0);
    gh.hold(std::make_unique<MyHeld>(sizeof(int32_t)));
    gh.transferHoldLists(1);
    gh.hold(std::make_unique<MyHeld>(sizeof(int32_t)));
    gh.transferHoldLists(2);
    gh.hold(std::make_unique<MyHeld>(sizeof(int32_t)));
    gh.transferHoldLists(4);
    EXPECT_EQ(4u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(0);
    EXPECT_EQ(4u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(1);
    EXPECT_EQ(3u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(2);
    EXPECT_EQ(2u * sizeof(int32_t), gh.getHeldBytes());
    gh.hold(std::make_unique<MyHeld>(sizeof(int32_t)));
    gh.transferHoldLists(6);
    EXPECT_EQ(3u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(6);
    EXPECT_EQ(1u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(7);
    EXPECT_EQ(0u * sizeof(int32_t), gh.getHeldBytes());
    gh.trimHoldLists(7);
    EXPECT_EQ(0u * sizeof(int32_t), gh.getHeldBytes());
}

GTEST_MAIN_RUN_ALL_TESTS()
