// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;

TEST(FastCellsTest, push_back_fast_works) {
    FastCells<float> cells(3);
    EXPECT_EQ(cells.capacity, 4);
    EXPECT_EQ(cells.size, 0);
    cells.push_back_fast(1.0);
    cells.push_back_fast(2.0);
    cells.push_back_fast(3.0);
    EXPECT_EQ(cells.capacity, 4);
    EXPECT_EQ(cells.size, 3);
    cells.ensure_free(3);
    EXPECT_EQ(cells.capacity, 8);
    EXPECT_EQ(cells.size, 3);
    cells.push_back_fast(4.0);
    cells.push_back_fast(5.0);
    cells.push_back_fast(6.0);
    EXPECT_EQ(cells.capacity, 8);
    EXPECT_EQ(cells.size, 6);
    auto usage = cells.estimate_extra_memory_usage();
    EXPECT_EQ(usage.allocatedBytes(), sizeof(float) * 8);
    EXPECT_EQ(usage.usedBytes(), sizeof(float) * 6);
    EXPECT_EQ(*cells.get(0), 1.0);
    EXPECT_EQ(*cells.get(1), 2.0);
    EXPECT_EQ(*cells.get(2), 3.0);
    EXPECT_EQ(*cells.get(3), 4.0);
    EXPECT_EQ(*cells.get(4), 5.0);
    EXPECT_EQ(*cells.get(5), 6.0);
}

TEST(FastCellsTest, add_cells_works) {
    FastCells<float> cells(3);
    auto arr1 = cells.add_cells(3);
    EXPECT_EQ(cells.capacity, 4);
    EXPECT_EQ(cells.size, 3);
    arr1[0] = 1.0;
    arr1[1] = 2.0;
    arr1[2] = 3.0;
    auto arr2 = cells.add_cells(3);
    EXPECT_EQ(cells.capacity, 8);
    EXPECT_EQ(cells.size, 6);
    arr2[0] = 4.0;
    arr2[1] = 5.0;
    arr2[2] = 6.0;
    EXPECT_EQ(*cells.get(0), 1.0);
    EXPECT_EQ(*cells.get(1), 2.0);
    EXPECT_EQ(*cells.get(2), 3.0);
    EXPECT_EQ(*cells.get(3), 4.0);
    EXPECT_EQ(*cells.get(4), 5.0);
    EXPECT_EQ(*cells.get(5), 6.0);
}

GTEST_MAIN_RUN_ALL_TESTS()
