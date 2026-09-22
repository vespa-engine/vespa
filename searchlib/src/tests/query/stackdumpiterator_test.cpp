// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for SimpleQueryStackDumpIterator, in particular the compressed int
// bounds checks used by readCompressedInt/readCompressedPositiveInt.

#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::ParseItem;
using search::SimpleQueryStackDumpIterator;

namespace {

// ITEM_TRUE has no content beyond the generic header, so it is a
// convenient vehicle for putting a compressed int (the weight) at
// the very end of the buffer.
constexpr uint8_t item_true_with_weight = static_cast<uint8_t>(ParseItem::ITEM_TRUE) | ParseItem::IF_WEIGHT;

} // namespace

TEST(StackDumpIteratorTest, negative_weight_encoded_in_a_single_byte_at_buffer_end_is_read) {
    // Compressed encoding of -1 is a single byte (0x81), see compress_test.cpp.
    // readCompressedInt must accept this even though no bytes follow.
    const char buf[] = {static_cast<char>(item_true_with_weight), static_cast<char>(0x81)};
    SimpleQueryStackDumpIterator it(std::string_view(buf, sizeof(buf)));
    ASSERT_TRUE(it.next());
    EXPECT_EQ(ParseItem::ITEM_TRUE, it.getType());
    EXPECT_EQ(-1, it.GetWeight().percent());
    EXPECT_FALSE(it.next());
}

TEST(StackDumpIteratorTest, positive_weight_encoded_in_a_single_byte_at_buffer_end_is_read) {
    // Sanity check the mirrored (positive) single-byte case still works.
    const char buf[] = {static_cast<char>(item_true_with_weight), static_cast<char>(0x01)};
    SimpleQueryStackDumpIterator it(std::string_view(buf, sizeof(buf)));
    ASSERT_TRUE(it.next());
    EXPECT_EQ(1, it.GetWeight().percent());
}

TEST(StackDumpIteratorTest, truncated_4_byte_positive_unique_id_is_rejected_not_overread) {
    // Compressed positive encoding of 0x4000 is 4 bytes: {0xc0, 0x00, 0x40, 0x00},
    // see compress_test.cpp. The trailing two bytes here are only present so
    // that a buggy bounds check (that thinks 2 bytes is enough) would read
    // real, decodable data instead of crashing outright; the iterator must
    // still refuse to read past the declared end of its buffer.
    constexpr uint8_t item_with_unique_id = static_cast<uint8_t>(ParseItem::ITEM_TRUE) | ParseItem::IF_UNIQUEID;
    const char        full_buf[] = {static_cast<char>(item_with_unique_id),
                                     static_cast<char>(0xc0),
                                     static_cast<char>(0x00),
                                     static_cast<char>(0x40),
                                     static_cast<char>(0x00)};
    // Truncate the view right after the first 2 bytes of the compressed number.
    std::string_view      truncated(full_buf, 3);
    SimpleQueryStackDumpIterator it(truncated);
    EXPECT_FALSE(it.next());
}

TEST(StackDumpIteratorTest, full_4_byte_positive_unique_id_is_read_correctly) {
    constexpr uint8_t item_with_unique_id = static_cast<uint8_t>(ParseItem::ITEM_TRUE) | ParseItem::IF_UNIQUEID;
    const char        buf[] = {static_cast<char>(item_with_unique_id),
                                static_cast<char>(0xc0),
                                static_cast<char>(0x00),
                                static_cast<char>(0x40),
                                static_cast<char>(0x00)};
    SimpleQueryStackDumpIterator it(std::string_view(buf, sizeof(buf)));
    ASSERT_TRUE(it.next());
    EXPECT_EQ(0x4000u, it.getUniqueId());
}

GTEST_MAIN_RUN_ALL_TESTS()
