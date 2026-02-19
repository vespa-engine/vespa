// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/bit_span.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

using vespalib::BitSpan;

namespace {

std::vector<int> list(std::vector<int> bits) { return bits; }
std::vector<uint8_t> pack(std::vector<int> bits) {
    size_t cnt = 0;
    uint8_t byte = 0;
    std::vector<uint8_t> result;
    for (bool bit: bits) {
        byte |= (uint8_t(bit) << (cnt % 8));
        if ((++cnt % 8) == 0) {
            result.push_back(byte);
            byte = 0;
        }
    }
    if ((cnt % 8) != 0) {
        result.push_back(byte);
    }
    return result;
}

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
auto my_bits = list({1, 1, 0, 0, 0, 1, 1, 1,
                     0, 0, 1, 1, 1, 0, 0, 0,
                     1, 1, 1, 1, 0, 0, 0, 0});
// shared test data, bits packed into 3 bytes
auto packed = pack(my_bits);

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
