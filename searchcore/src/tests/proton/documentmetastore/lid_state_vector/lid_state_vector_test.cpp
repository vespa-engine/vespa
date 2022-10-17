// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/documentmetastore/lidstatevector.h>
#include <vespa/searchcore/proton/documentmetastore/lid_hold_list.h>
#include <vespa/vespalib/util/generationholder.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::GenerationHolder;

namespace proton {

class LidStateVectorTest : public ::testing::Test
{
protected:
    GenerationHolder _gen_hold;

    LidStateVectorTest()
        : ::testing::Test(),
          _gen_hold()
    {
    }

    ~LidStateVectorTest()
    {
        _gen_hold.reclaim_all();
    }

};


TEST_F(LidStateVectorTest, basic_free_list_is_working)
{
    LidStateVector freeLids(100, 100, _gen_hold, true, false);
    LidHoldList list;
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQ(0u, freeLids.count());
    EXPECT_EQ(0u, list.size());

    list.add(10, 10);
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQ(0u, freeLids.count());
    EXPECT_EQ(1u, list.size());

    list.add(20, 20);
    list.add(30, 30);
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQ(0u, freeLids.count());
    EXPECT_EQ(3u, list.size());

    list.reclaim_memory(20, freeLids);
    EXPECT_FALSE(freeLids.empty());
    EXPECT_EQ(1u, freeLids.count());

    EXPECT_EQ(10u, freeLids.getLowest());
    freeLids.clearBit(10);
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQ(0u, freeLids.count());
    EXPECT_EQ(2u, list.size());

    list.reclaim_memory(31, freeLids);
    EXPECT_FALSE(freeLids.empty());
    EXPECT_EQ(2u, freeLids.count());

    EXPECT_EQ(20u, freeLids.getLowest());
    freeLids.clearBit(20);
    EXPECT_FALSE(freeLids.empty());
    EXPECT_EQ(1u, freeLids.count());
    EXPECT_EQ(0u, list.size());

    EXPECT_EQ(30u, freeLids.getLowest());
    freeLids.clearBit(30);
    EXPECT_TRUE(freeLids.empty());
    EXPECT_EQ(0u, list.size());
    EXPECT_EQ(0u, freeLids.count());
}

void
assertLidStateVector(const std::vector<uint32_t> &expLids, uint32_t lowest, uint32_t highest,
                     const LidStateVector &actLids)
{
    if (!expLids.empty()) {
        EXPECT_EQ(expLids.size(), actLids.count());
        uint32_t trueBit = 0;
        for (auto i : expLids) {
            EXPECT_TRUE(actLids.testBit(i));
            trueBit = actLids.getNextTrueBit(trueBit);
            EXPECT_EQ(i, trueBit);
            ++trueBit;
        }
        trueBit = actLids.getNextTrueBit(trueBit);
        EXPECT_EQ(actLids.size(), trueBit);
    } else {
        EXPECT_TRUE(actLids.empty());
    }
    EXPECT_EQ(lowest, actLids.getLowest());
    EXPECT_EQ(highest, actLids.getHighest());
}

TEST_F(LidStateVectorTest, lid_state_vector_resizing_is_working)
{
    LidStateVector lids(1000, 1000, _gen_hold, true, true);
    lids.setBit(3);
    lids.setBit(150);
    lids.setBit(270);
    lids.setBit(310);
    lids.setBit(440);
    lids.setBit(780);
    lids.setBit(930);
    assertLidStateVector({3,150,270,310,440,780,930}, 3, 930, lids);

    lids.resizeVector(1500, 1500);
    assertLidStateVector({3,150,270,310,440,780,930}, 3, 930, lids);
    lids.clearBit(3);
    assertLidStateVector({150,270,310,440,780,930}, 150, 930, lids);
    lids.clearBit(150);
    assertLidStateVector({270,310,440,780,930}, 270, 930, lids);
    lids.setBit(170);
    assertLidStateVector({170,270,310,440,780,930}, 170, 930, lids);
    lids.setBit(1490);
    assertLidStateVector({170,270,310,440,780,930,1490}, 170, 1490, lids);

    lids.resizeVector(2000, 2000);
    assertLidStateVector({170,270,310,440,780,930,1490}, 170, 1490, lids);
    lids.clearBit(170);
    assertLidStateVector({270,310,440,780,930,1490}, 270, 1490, lids);
    lids.clearBit(270);
    assertLidStateVector({310,440,780,930,1490}, 310, 1490, lids);
    lids.setBit(1990);
    assertLidStateVector({310,440,780,930,1490,1990}, 310, 1990, lids);
    lids.clearBit(310);
    assertLidStateVector({440,780,930,1490,1990}, 440, 1990, lids);
    lids.clearBit(440);
    assertLidStateVector({780,930,1490,1990}, 780, 1990, lids);
    lids.clearBit(780);
    assertLidStateVector({930,1490,1990}, 930, 1990, lids);
    lids.clearBit(930);
    assertLidStateVector({1490,1990}, 1490, 1990, lids);
    lids.clearBit(1490);
    assertLidStateVector({1990}, 1990, 1990, lids);
    lids.clearBit(1990);
    assertLidStateVector({}, 2000, 0, lids);
}

TEST_F(LidStateVectorTest, set_bits)
{
    LidStateVector lids(1000, 1000, _gen_hold, true, true);
    EXPECT_EQ(100, lids.assert_not_set_bits({ 10, 40, 100 }));
    assertLidStateVector({}, 1000, 0, lids);
    EXPECT_EQ(100, lids.set_bits({ 10, 40, 100 }));
    assertLidStateVector({ 10, 40, 100 }, 10, 100, lids);
}

TEST_F(LidStateVectorTest, clear_bits)
{
    LidStateVector lids(1000, 1000, _gen_hold, true, true);
    lids.set_bits({ 10, 40, 100 });
    lids.clear_bits({ 10, 100 });
    assertLidStateVector({ 40 }, 40, 40, lids);
}

TEST_F(LidStateVectorTest, consider_clear_bits)
{
    LidStateVector lids(1000, 1000, _gen_hold, true, true);
    lids.set_bits({ 40 });
    lids.consider_clear_bits({ 10, 100 });
    assertLidStateVector({ 40 }, 40, 40, lids);
    lids.consider_clear_bits({ 10, 40, 100 });
    assertLidStateVector({}, 1000, 0, lids);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
