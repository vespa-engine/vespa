// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/bit_span.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

using vespalib::BitSpan;

TEST(BitSpanTest, empty_span)
{
    BitSpan span;
    EXPECT_EQ(0u, span.size());
    EXPECT_TRUE(span.empty());
}

TEST(BitSpanTest, empty_span_with_data)
{
    char data[1] = {static_cast<char>(0xFF)};
    BitSpan span(data, 0);
    EXPECT_TRUE(span.empty());
    EXPECT_FALSE(span.begin() != span.end());
}

TEST(BitSpanTest, multi_byte)
{
    // byte 0: 0b10100101 = 0xA5, byte 1: 0b00110011 = 0x33
    char data[2] = {static_cast<char>(0xA5), static_cast<char>(0x33)};
    BitSpan span(data, 16);
    EXPECT_EQ(16u, span.size());
    EXPECT_FALSE(span.empty());
    std::vector<bool> expect = {1,0,1,0,0,1,0,1, 1,1,0,0,1,1,0,0};
    ASSERT_EQ(span.size(), expect.size());
    for (uint32_t i = 0; i < expect.size(); ++i) {
        EXPECT_EQ(span[i], expect[i]) << "index " << i;
    }
    std::vector<bool> result;
    for (bool v : span) {
        result.push_back(v);
    }
    EXPECT_EQ(result, expect);
}

TEST(BitSpanTest, partial_span_across_byte_boundary)
{
    char data[2] = {static_cast<char>(0xFF), static_cast<char>(0x01)};
    BitSpan span(data, 10);
    EXPECT_EQ(10u, span.size());
    EXPECT_FALSE(span.empty());
    std::vector<bool> expect = {1,1,1,1,1,1,1,1, 1,0};
    ASSERT_EQ(span.size(), expect.size());
    for (uint32_t i = 0; i < expect.size(); ++i) {
        EXPECT_EQ(span[i], expect[i]) << "index " << i;
    }
    std::vector<bool> result;
    for (bool v : span) {
        result.push_back(v);
    }
    EXPECT_EQ(result, expect);
}
