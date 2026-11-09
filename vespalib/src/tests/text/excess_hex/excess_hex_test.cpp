// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/text/excess_hex.h>

#include <cmath>
#include <limits>

using vespalib::excess_hex::decode;

TEST(ExcessHexTest, int32_values_are_decoded) {
    EXPECT_EQ(0, decode<int32_t>("80000000"));
    EXPECT_EQ(7, decode<int32_t>("80000007"));
    EXPECT_EQ(-1, decode<int32_t>("7fffffff"));
    EXPECT_EQ(255, decode<int32_t>("800000ff"));
    EXPECT_EQ(std::numeric_limits<int32_t>::min(), decode<int32_t>("00000000"));
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), decode<int32_t>("ffffffff"));
}

TEST(ExcessHexTest, int64_values_are_decoded) {
    EXPECT_EQ(0, decode<int64_t>("8000000000000000"));
    EXPECT_EQ(5, decode<int64_t>("8000000000000005"));
    EXPECT_EQ(-2, decode<int64_t>("7ffffffffffffffe"));
    EXPECT_EQ(std::numeric_limits<int64_t>::min(), decode<int64_t>("0000000000000000"));
    EXPECT_EQ(std::numeric_limits<int64_t>::max(), decode<int64_t>("ffffffffffffffff"));
}

TEST(ExcessHexTest, upper_and_lower_case_hex_digits_are_accepted) {
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), decode<int32_t>("FFFFFFFF"));
    EXPECT_EQ(-1, decode<int32_t>("7FffFFff"));
    EXPECT_EQ(2.5, decode<double>("C004000000000000"));
}

TEST(ExcessHexTest, float_values_are_decoded) {
    EXPECT_EQ(1.0f, decode<float>("bf800000"));
    EXPECT_EQ(-1.0f, decode<float>("407fffff"));
    EXPECT_EQ(std::numeric_limits<float>::infinity(), decode<float>("ff800000"));
    EXPECT_EQ(-std::numeric_limits<float>::infinity(), decode<float>("007fffff"));
    auto pos_zero = decode<float>("80000000");
    ASSERT_TRUE(pos_zero.has_value());
    EXPECT_EQ(0.0f, *pos_zero);
    EXPECT_FALSE(std::signbit(*pos_zero));
    auto neg_zero = decode<float>("7fffffff");
    ASSERT_TRUE(neg_zero.has_value());
    EXPECT_EQ(0.0f, *neg_zero);
    EXPECT_TRUE(std::signbit(*neg_zero));
}

TEST(ExcessHexTest, double_values_are_decoded) {
    EXPECT_EQ(2.5, decode<double>("c004000000000000"));
    EXPECT_EQ(-0.25, decode<double>("402fffffffffffff"));
    EXPECT_EQ(0.1, decode<double>("bfb999999999999a"));
    EXPECT_EQ(std::numeric_limits<double>::infinity(), decode<double>("fff0000000000000"));
    EXPECT_EQ(-std::numeric_limits<double>::infinity(), decode<double>("000fffffffffffff"));
}

TEST(ExcessHexTest, nan_is_decoded) {
    auto nan = decode<double>("fff8000000000000");
    ASSERT_TRUE(nan.has_value());
    EXPECT_TRUE(std::isnan(*nan));
}

TEST(ExcessHexTest, ordering_of_encoded_strings_matches_ordering_of_values) {
    // Lexicographically increasing encodings must give increasing values.
    const char* doubles[] = {"000fffffffffffff", "402fffffffffffff", "7fffffffffffffff", "8000000000000000",
                             "bfb999999999999a", "c004000000000000", "fff0000000000000"};
    for (size_t i = 1; i < std::size(doubles); ++i) {
        EXPECT_LE(*decode<double>(doubles[i - 1]), *decode<double>(doubles[i])) << doubles[i];
    }
    const char* ints[] = {"00000000", "7ffffffe", "7fffffff", "80000000", "80000001", "ffffffff"};
    for (size_t i = 1; i < std::size(ints); ++i) {
        EXPECT_LT(*decode<int32_t>(ints[i - 1]), *decode<int32_t>(ints[i])) << ints[i];
    }
}

TEST(ExcessHexTest, wrong_length_is_rejected) {
    EXPECT_FALSE(decode<int32_t>(""));
    EXPECT_FALSE(decode<int32_t>("8000000"));
    EXPECT_FALSE(decode<int32_t>("800000000"));
    EXPECT_FALSE(decode<int32_t>("8000000000000000"));
    EXPECT_FALSE(decode<int64_t>("80000000"));
    EXPECT_FALSE(decode<float>("8000000000000000"));
    EXPECT_FALSE(decode<double>("80000000"));
}

TEST(ExcessHexTest, non_hex_characters_are_rejected) {
    EXPECT_FALSE(decode<int32_t>("8000000g"));
    EXPECT_FALSE(decode<int32_t>("x0000000"));
    EXPECT_FALSE(decode<int32_t>("-0000000"));
    EXPECT_FALSE(decode<int32_t>("+0000000"));
    EXPECT_FALSE(decode<int32_t>(" 0000000"));
    EXPECT_FALSE(decode<int32_t>("0000000 "));
    EXPECT_FALSE(decode<int64_t>("0x00000000000000"));
    EXPECT_FALSE(decode<double>("80000000-0000000"));
}

GTEST_MAIN_RUN_ALL_TESTS()
