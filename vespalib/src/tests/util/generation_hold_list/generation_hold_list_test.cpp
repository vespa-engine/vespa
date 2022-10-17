// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generation_hold_list.hpp>
#include <vespa/vespalib/util/generationholder.h>
#include <cstdint>

using namespace vespalib;

using MyElem = GenerationHeldBase;
using generation_t = GenerationHandler::generation_t;

TEST(GenerationHolderTest, holding_of_unique_ptr_elements_with_tracking_of_held_bytes)
{
    GenerationHolder h;
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

TEST(GenerationHolderTest, reclaim_all_clears_everything)
{
    GenerationHolder h;
    h.insert(std::make_unique<MyElem>(3));
    h.insert(std::make_unique<MyElem>(5));
    h.assign_generation(1);
    h.reclaim_all();
    EXPECT_EQ(0, h.get_held_bytes());
}

using IntVector = std::vector<int32_t>;
using IntHoldList = GenerationHoldList<int32_t, false, true>;

struct IntHoldListTest : public testing::Test {
    IntHoldList h;
    IntHoldListTest() : h() {}
    void assert_reclaim(const IntVector& exp, generation_t oldest_used_gen) {
        IntVector act;
        h.reclaim(oldest_used_gen, [&](int elem){ act.push_back(elem); });
        EXPECT_EQ(exp, act);
    }
    void assert_reclaim_all(const IntVector& exp) {
        IntVector act;
        h.reclaim_all([&](int elem){ act.push_back(elem); });
        EXPECT_EQ(exp, act);
    }
};

TEST_F(IntHoldListTest, reclaim_calls_callback_for_reclaimed_elements)
{
    h.insert(3);
    h.assign_generation(1);
    h.insert(5);
    h.insert(7);
    h.assign_generation(2);

    assert_reclaim({}, 1);
    assert_reclaim({3}, 2);
    assert_reclaim({5, 7}, 3);
}

TEST_F(IntHoldListTest, reclaim_all_calls_callback_for_all_elements)
{
    h.insert(3);
    h.insert(5);
    h.assign_generation(2);
    assert_reclaim_all({3, 5});
    assert_reclaim_all({});
}

GTEST_MAIN_RUN_ALL_TESTS()
