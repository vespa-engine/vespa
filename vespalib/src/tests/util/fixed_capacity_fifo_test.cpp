// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/fixed_capacity_fifo.hpp>
#include <vespa/vespalib/util/fifo_queue.h> // TODO move
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>
#include <queue>

using namespace ::testing;

namespace vespalib {

TEST(FixedCapacityFifoTest, initial_state_is_empty) {
    FixedCapacityFifo<uint32_t> buf(16);
    EXPECT_TRUE(buf.empty());
    EXPECT_FALSE(buf.full());
    EXPECT_EQ(buf.size(), 0);
    EXPECT_EQ(buf.capacity(), 16);
    auto first = buf.begin();
    auto last = buf.end();
    EXPECT_TRUE(first == last);
}

TEST(FixedCapacityFifoTest, can_push_and_pop_single) {
    FixedCapacityFifo<uint32_t> buf(4);
    ASSERT_EQ(buf.capacity(), 4);
    buf.emplace_back(123);
    EXPECT_FALSE(buf.empty());
    EXPECT_FALSE(buf.full());
    ASSERT_EQ(buf.size(), 1);
    ASSERT_EQ(buf.front(), 123);
    buf.pop_front();
    EXPECT_TRUE(buf.empty());
    EXPECT_FALSE(buf.full());
    EXPECT_EQ(buf.size(), 0);
}

TEST(FixedCapacityFifoTest, can_push_and_pop_until_full) {
    FixedCapacityFifo<uint32_t> buf(4);
    ASSERT_EQ(buf.capacity(), 4);
    EXPECT_THAT(buf, ElementsAre());
    buf.emplace_back(1);
    fprintf(stderr, "1\n");
    EXPECT_THAT(buf, ElementsAre(1));
    buf.emplace_back(2);
    fprintf(stderr, "2\n");
    EXPECT_THAT(buf, ElementsAre(1, 2));
    buf.emplace_back(3);
    fprintf(stderr, "3\n");
    EXPECT_THAT(buf, ElementsAre(1, 2, 3));
    buf.emplace_back(4);
    fprintf(stderr, "4\n");
    EXPECT_THAT(buf, ElementsAre(1, 2, 3, 4));
    ASSERT_FALSE(buf.empty());
    ASSERT_TRUE(buf.full());
    ASSERT_EQ(buf.size(), 4);

    EXPECT_EQ(buf.front(), 1);
    buf.pop_front();
    fprintf(stderr, "5\n");
    EXPECT_THAT(buf, ElementsAre(2, 3, 4));
    EXPECT_EQ(buf.front(), 2);
    buf.pop_front();
    fprintf(stderr, "6\n");
    EXPECT_THAT(buf, ElementsAre(3, 4));
    EXPECT_EQ(buf.front(), 3);
    buf.pop_front();
    fprintf(stderr, "7\n");
    EXPECT_THAT(buf, ElementsAre(4));
    EXPECT_EQ(buf.front(), 4);
    buf.pop_front();
    fprintf(stderr, "8\n");
    EXPECT_THAT(buf, ElementsAre());

    EXPECT_TRUE(buf.empty());
    EXPECT_FALSE(buf.full());
    EXPECT_EQ(buf.size(), 0);
}

TEST(FixedCapacityFifoTest, push_and_pop_can_rotate_around) {
    FixedCapacityFifo<uint32_t> buf(4);
    buf.emplace_back(1);
    buf.emplace_back(2);
    buf.emplace_back(3);
    buf.emplace_back(4);
    buf.pop_front();
    buf.emplace_back(5);
    EXPECT_EQ(buf.front(), 2);
    EXPECT_THAT(buf, ElementsAre(2, 3, 4, 5));
    buf.pop_front();
    buf.emplace_back(6);
    EXPECT_EQ(buf.front(), 3);
    EXPECT_THAT(buf, ElementsAre(3, 4, 5, 6));
    buf.pop_front();
    buf.emplace_back(7);
    EXPECT_EQ(buf.front(), 4);
    EXPECT_THAT(buf, ElementsAre(4, 5, 6, 7));
    buf.pop_front();
    buf.emplace_back(8);
    EXPECT_EQ(buf.front(), 5);
    EXPECT_THAT(buf, ElementsAre(5, 6, 7, 8));
    buf.pop_front();
    buf.emplace_back(9);
    EXPECT_EQ(buf.front(), 6);
    EXPECT_THAT(buf, ElementsAre(6, 7, 8, 9));
    // ... and so it goes.
}

// TODO move
TEST(FifoQueueTest, prototyping) {
    FifoQueue<uint32_t> q(4);
    for (size_t i = 0; i < 64; ++i) {
        q.emplace_back(i);
    }
    ASSERT_EQ(q.size(), 64);
    EXPECT_EQ(q.front(), 0);
}

}

