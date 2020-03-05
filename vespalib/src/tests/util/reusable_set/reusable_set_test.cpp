// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/reusable_set_pool.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

using Mark = ReusableSet::Mark;

void verify_set(const ReusableSet &set, size_t sz, Mark val, size_t marked) {
    EXPECT_EQ(sz, set.sz);
    EXPECT_EQ(val, set.curval);
    size_t count = 0;
    for (size_t i = 0; i < set.sz; ++i) {
        if (set.isMarked(i)) ++count;
    }
    EXPECT_EQ(marked, count);
}

void verify_handle(const ReusableSetHandle &set, size_t sz, Mark val, size_t marked) {
    EXPECT_EQ(sz, set.capacity());
    EXPECT_EQ(val, set.generation());
    size_t count = 0;
    for (size_t i = 0; i < set.capacity(); ++i) {
        if (set.isMarked(i)) ++count;
    }
    EXPECT_EQ(marked, count);
}

class Pool : public ::testing::Test {
public:
    ReusableSetPool pool;
    Pool() : pool() {}
    ~Pool() {}
    void exercise(ReusableSetHandle &set) {
        size_t sz = set.capacity();
        size_t count = 0;
        for (size_t i = 0; i < sz; ++i) {
            if (set.isMarked(i)) ++count;
        }
        EXPECT_EQ(0, count);
        for (int i = 0; i < 17; ++i) {
            set.mark((i * 711) % sz);
        }
        count = 0;
        for (size_t i = 0; i < sz; ++i) {
            if (set.isMarked(i)) ++count;
        }
        EXPECT_EQ(17, count);
        for (int i = 0; i < 17; ++i) {
            set.mark((i * 711) % sz);
        }
        count = 0;
        for (size_t i = 0; i < sz; ++i) {
            if (set.isMarked(i)) ++count;
        }
        EXPECT_EQ(17, count);
   }
};


TEST(ReusableSetTest, simple_usage)
{
    ReusableSet visited(7);
    verify_set(visited, 7, 1, 0);
    visited.mark(1);
    visited.mark(2);
    visited.mark(4);
    verify_set(visited, 7, 1, 3);
    visited.mark(4);
    visited.mark(1);
    visited.mark(2);
    verify_set(visited, 7, 1, 3);
    visited.clear();
    verify_set(visited, 7, 2, 0);
    visited.clear();
    verify_set(visited, 7, 3, 0);
}

TEST_F(Pool, reuse_works)
{
    for (int i = 0; i < 65535; ++i) {
        auto handle = pool.get(7);
        EXPECT_EQ(i, pool.reuse_count());
        EXPECT_EQ(1, pool.create_count());
        verify_handle(handle, 250, i+1, 0);
        exercise(handle);
    }
    for (int i = 0; i < 5; ++i) {
        auto handle = pool.get(7);
        EXPECT_EQ(65535+i, pool.reuse_count());
        EXPECT_EQ(1, pool.create_count());
        verify_handle(handle, 250, i+1, 0);
        exercise(handle);
    }
    auto handle3 = pool.get(300);
    EXPECT_EQ(2, pool.create_count());
    verify_handle(handle3, 600, 1, 0);
    exercise(handle3);
    auto handle7 = pool.get(700);
    EXPECT_EQ(3, pool.create_count());
    verify_handle(handle7, 1400, 1, 0);
    exercise(handle7);
}

GTEST_MAIN_RUN_ALL_TESTS()
