// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/overflow.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cstdint>

using namespace ::testing;

namespace vespalib {

TEST(OverflowTest, add_overflow_is_detected) {
    EXPECT_FALSE(add_would_overflow<uint8_t>(uint8_t{100}, uint8_t{155}));
    EXPECT_FALSE(add_would_overflow<uint8_t>(100, 155));
    EXPECT_FALSE(add_would_overflow<uint8_t>(uint8_t{0}, uint8_t{255}));
    EXPECT_TRUE( add_would_overflow<uint8_t>(uint8_t{101}, uint8_t{155}));
    EXPECT_TRUE( add_would_overflow<uint8_t>(101, 155)); // also works with implicit int operands and intermediate result
    EXPECT_FALSE(add_would_overflow<int32_t>(int32_t{INT32_MAX}, int32_t{0}));
    EXPECT_TRUE( add_would_overflow<int32_t>(int32_t{INT32_MAX}, int32_t{1}));
    EXPECT_FALSE(add_would_overflow<int64_t>(int64_t{INT64_MAX}, int64_t{0}));
    EXPECT_FALSE(add_would_overflow<int64_t>(INT64_MAX, 0));
    EXPECT_TRUE( add_would_overflow<int64_t>(int64_t{INT64_MAX}, int64_t{1}));
    EXPECT_TRUE( add_would_overflow<int64_t>(INT64_MAX, 1));
    EXPECT_TRUE( add_would_overflow<uint64_t>(uint64_t{UINT64_MAX}, uint64_t{1}));

    EXPECT_TRUE( add_would_overflow<int32_t>(INT32_MIN, -1));
}

TEST(OverflowTest, sub_underflow_is_detected) {
    EXPECT_FALSE(sub_would_underflow<uint8_t>(uint8_t{100}, uint8_t{100}));
    EXPECT_TRUE( sub_would_underflow<uint8_t>(uint8_t{100}, uint8_t{101}));
    EXPECT_FALSE(sub_would_underflow<uint64_t>(uint64_t{1}, uint64_t{0}));
    EXPECT_TRUE( sub_would_underflow<uint64_t>(uint64_t{0}, uint64_t{1}));
    EXPECT_FALSE(sub_would_underflow<int64_t>(int64_t{0}, int64_t{1}));
    EXPECT_FALSE(sub_would_underflow<int64_t>(int64_t{-1}, int64_t{INT64_MAX}));
    EXPECT_TRUE( sub_would_underflow<int64_t>(int64_t{-2}, int64_t{INT64_MAX}));

    EXPECT_TRUE( sub_would_underflow<int32_t>(INT32_MAX, -1));
}

TEST(OverflowTest, mul_overflow_is_detected) {
    EXPECT_FALSE(mul_would_overflow<uint8_t>(uint8_t{50}, uint8_t{5}));
    EXPECT_TRUE( mul_would_overflow<uint8_t>(uint8_t{50}, uint8_t{6}));
    EXPECT_FALSE(mul_would_overflow<int64_t>(int64_t{INT64_MAX}, int64_t{1}));
    EXPECT_TRUE( mul_would_overflow<int64_t>(int64_t{INT64_MAX}, int64_t{2}));
    EXPECT_TRUE( mul_would_overflow<int64_t>(int64_t{INT64_MAX}, int64_t{INT64_MAX}));
}

}
