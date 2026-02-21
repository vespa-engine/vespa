// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/bit_span.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

using vespalib::BitSpan;

namespace {

std::vector<int> extract_with_range(BitSpan span) {
    std::vector<int> result;
    for (bool bit: span) {
        result.push_back(bit);
    }
    return result;
}

std::vector<int> extract_with_loop(BitSpan span) {
    std::vector<int> result;
    for (size_t i = 0; i < span.size(); ++i) {
        result.push_back(span[i]);
    }
    return result;
}

// shared test data, one int per bit
std::vector<int> my_bits = {1, 1, 0, 0, 0, 1, 1, 1,
                            0, 0, 1, 1, 1, 0, 0, 0,
                            1, 1, 1, 1, 0, 0, 0, 0};

// shared test data, bits packed into 3 bytes (NOTE: LSB first)
std::vector<std::byte> packed = {std::byte{0b11100011},
                                 std::byte{0b00011100},
                                 std::byte{0b00001111}};

} // namespace

TEST(BitSpanTest, empty_span)
{
    BitSpan span;
    EXPECT_EQ(0u, span.size());
    EXPECT_TRUE(span.empty());
    EXPECT_FALSE(span.begin() != span.end());
}

TEST(BitSpanTest, empty_span_with_offset)
{
    BitSpan span(nullptr, 100, 0);
    EXPECT_EQ(0u, span.size());
    EXPECT_TRUE(span.empty());
    EXPECT_FALSE(span.begin() != span.end());
}

TEST(BitSpanTest, span_with_all_the_bits)
{
    BitSpan span(packed.data(), 3 * 8);
    EXPECT_FALSE(span.empty());
    EXPECT_EQ(span.size(), 3 * 8);
    std::vector<int> expected = my_bits;
    EXPECT_EQ(extract_with_range(span), expected);
    EXPECT_EQ(extract_with_loop(span), expected);
}

TEST(BitSpanTest, span_with_padding)
{
    BitSpan span(packed.data(), 17);
    EXPECT_FALSE(span.empty());
    EXPECT_EQ(span.size(), 17);
    std::vector<int> expected(my_bits.begin(), my_bits.begin() + 17);
    EXPECT_EQ(extract_with_range(span), expected);
    EXPECT_EQ(extract_with_loop(span), expected);
}

TEST(BitSpanTest, span_with_padding_and_offset)
{
    BitSpan span(packed.data(), 5, 11);
    EXPECT_FALSE(span.empty());
    EXPECT_EQ(span.size(), 11);
    std::vector<int> expected(my_bits.begin() + 5, my_bits.begin() + (5 + 11));
    EXPECT_EQ(extract_with_range(span), expected);
    EXPECT_EQ(extract_with_loop(span), expected);
}
