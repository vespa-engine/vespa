// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/test/cell_type_space.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;

//-----------------------------------------------------------------------------

auto all_types = CellTypeUtils::list_types();

//-----------------------------------------------------------------------------

TEST(CellTypeSpaceTest, n_1) {
    auto space = CellTypeSpace(all_types, 1);
    for (auto t0: all_types) {
        ASSERT_TRUE(space.valid());
        auto ts = space.get();
        ASSERT_EQ(ts.size(), 1);
        EXPECT_EQ(t0, ts[0]);
        space.next();
    }
    EXPECT_FALSE(space.valid());
}

TEST(CellTypeSpaceTest, n_2) {
    auto space = CellTypeSpace(all_types, 2);
    for (auto t0: all_types) {
        for (auto t1: all_types) {
            ASSERT_TRUE(space.valid());
            auto ts = space.get();
            ASSERT_EQ(ts.size(), 2);
            EXPECT_EQ(t0, ts[0]);
            EXPECT_EQ(t1, ts[1]);
            space.next();
        }
    }
    EXPECT_FALSE(space.valid());
}

TEST(CellTypeSpaceTest, n_2_same) {
    auto space = CellTypeSpace(all_types, 2).same();
    for (auto t0: all_types) {
        for (auto t1: all_types) {
            if (t0 != t1) continue;
            ASSERT_TRUE(space.valid());
            auto ts = space.get();
            ASSERT_EQ(ts.size(), 2);
            EXPECT_EQ(t0, ts[0]);
            EXPECT_EQ(t1, ts[1]);
            space.next();
        }
    }
    EXPECT_FALSE(space.valid());
}

TEST(CellTypeSpaceTest, n_2_different) {
    auto space = CellTypeSpace(all_types, 2).different();
    for (auto t0: all_types) {
        for (auto t1: all_types) {
            if (t0 == t1) continue;
            ASSERT_TRUE(space.valid());
            auto ts = space.get();
            ASSERT_EQ(ts.size(), 2);
            EXPECT_EQ(t0, ts[0]);
            EXPECT_EQ(t1, ts[1]);
            space.next();
        }
    }
    EXPECT_FALSE(space.valid());
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
