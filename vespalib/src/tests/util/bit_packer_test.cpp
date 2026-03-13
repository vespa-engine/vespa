// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/bit_packer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

using vespalib::BitPacker;
using vespalib::BitSpan;

namespace {

std::vector<bool> my_bits = {1, 1, 0, 0, 0, 1, 1, 1,
                             0, 0, 1, 1, 1, 0, 0, 0,
                             1, 1, 1, 1, 0, 0, 0, 0};

}

TEST(BitPackerTest, bits_can_be_packed)
{
    BitPacker packer;
    EXPECT_TRUE(packer.empty());
    EXPECT_EQ(packer.size(), 0);
    EXPECT_EQ(packer.storage().size(), 0);
    for (int i = 0; i < 24; ++i) {
        packer.push_back(my_bits[i]);
        int bitcnt = i + 1;
        EXPECT_FALSE(packer.empty());
        EXPECT_EQ(packer.size(), bitcnt);
        EXPECT_EQ(packer.storage().size(), (bitcnt + 7) / 8);
    }
    EXPECT_EQ(packer.size(), 24);
    ASSERT_EQ(packer.storage().size(), 3);
    EXPECT_EQ(packer.storage()[0], std::byte{0b11100011});
    EXPECT_EQ(packer.storage()[1], std::byte{0b00011100});
    EXPECT_EQ(packer.storage()[2], std::byte{0b00001111});
}

TEST(BitPackerTest, bit_span_can_be_created) {
    BitPacker packer;
    for (int i = 0; i < 24; ++i) {
        packer.push_back(my_bits[i]);
    }
    auto span = packer.bit_span(10, 9);
    ASSERT_EQ(span.size(), 9);
    for (int i = 0; i < 9; ++ i) {
        int idx = i + 10;
        EXPECT_EQ(bool(my_bits[idx]), span[i]);
    }
}

TEST(BitPackerTest, bit_spans_are_clamped) {
    BitPacker packer;
    for (int i = 0; i < 24; ++i) {
        packer.push_back(my_bits[i]);
    }
    auto span = packer.bit_span(16, 100);
    ASSERT_EQ(span.size(), 8);
    for (int i = 0; i < 8; ++ i) {
        int idx = i + 16;
        EXPECT_EQ(bool(my_bits[idx]), span[i]);
    }
    EXPECT_EQ(packer.bit_span(100, 16).size(), 0);
}
