// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generation_hold_list.hpp>
#include <vespa/vespalib/util/generationholder.h>

using vespalib::GenerationHeldBase;
using vespalib::GenerationHoldList;

using MyElem = GenerationHeldBase;
using MyHoldList = GenerationHoldList<MyElem::UP, true>;

TEST(GenerationHoldListTest, holding_of_unique_ptr_elements_with_tracking_of_held_bytes)
{
    MyHoldList h;
    h.insert(std::make_unique<MyElem>(3));
    h.assign_generation(0);
    h.insert(std::make_unique<MyElem>(5));
    h.assign_generation(1);
    h.insert(std::make_unique<MyElem>(7));
    h.assign_generation(2);
    h.insert(std::make_unique<MyElem>(11));
    h.assign_generation(4);
    EXPECT_EQ(3 + 5 + 7 + 11, h.get_held_bytes());

    h.reclaim(0);
    EXPECT_EQ(3 + 5 + 7 + 11, h.get_held_bytes());
    h.reclaim(1);
    EXPECT_EQ(5 + 7 + 11, h.get_held_bytes());
    h.reclaim(2);
    EXPECT_EQ(7 + 11, h.get_held_bytes());

    h.insert(std::make_unique<MyElem>(13));
    h.assign_generation(6);
    EXPECT_EQ(7 + 11 + 13, h.get_held_bytes());

    h.reclaim(6);
    EXPECT_EQ(13, h.get_held_bytes());
    h.reclaim(7);
    EXPECT_EQ(0, h.get_held_bytes());
    h.reclaim(7);
    EXPECT_EQ(0, h.get_held_bytes());
}

GTEST_MAIN_RUN_ALL_TESTS()
