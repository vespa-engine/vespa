// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/bitcompression/countcompression.h>
#include <vespa/searchlib/diskindex/features_size_flush.h>
#include <vespa/searchlib/test/diskindex/compressed_read_buffer.h>
#include <vespa/searchlib/test/diskindex/compressed_write_buffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>

using search::index::PostingListCounts;
using search::ComprFileWriteContext;
using search::diskindex::test::CompressedReadBuffer;
using search::diskindex::test::CompressedWriteBuffer;

namespace search::index {

void PrintTo(const PostingListCounts& counts, std::ostream* os) {
    *os << "{" << counts._numDocs << "," << counts._bitLength << ",[";
    bool first = true;
    for (auto& seg : counts._segments) {
        if (first) {
            first = false;
        } else {
            *os << ",";
        }
        *os << "{" << seg._numDocs << "," << seg._bitLength << "," << seg._lastDoc << "}";
    }
    *os << "]}";
}

}

namespace {

constexpr uint32_t chunk_size = 256_Ki;
constexpr uint64_t num_word_ids = 10_Mi;

}

class PostingListCountsTest : public ::testing::Test {
protected:
    using EncodeContext = search::bitcompression::PostingListCountFileEncodeContext;
    using DecodeContext = search::bitcompression::PostingListCountFileDecodeContext;
    using ReadBuffer = search::diskindex::test::CompressedReadBuffer<true>;
    using WriteBuffer = search::diskindex::test::CompressedWriteBuffer<true>;

    EncodeContext         _ec;
    WriteBuffer           _wb;
    DecodeContext         _dc;
    ReadBuffer            _rb;

    PostingListCountsTest();
    ~PostingListCountsTest() override;
    void flush();
    void write(PostingListCounts &counts) { _ec.writeCounts(counts); }
    PostingListCounts read() { PostingListCounts counts; _dc.readCounts(counts); return counts; }
};

PostingListCountsTest::PostingListCountsTest()
    : ::testing::Test(),
      _ec(),
      _wb(_ec),
      _dc(),
      _rb(_dc, _wb)
{
    _ec._minChunkDocs = chunk_size;
    _ec._numWordIds = num_word_ids;
    _dc._minChunkDocs = chunk_size;
    _dc._numWordIds = num_word_ids;
}

PostingListCountsTest::~PostingListCountsTest() = default;

void
PostingListCountsTest::flush()
{
    _wb.flush();
    _rb.rewind(_wb);
}

TEST_F(PostingListCountsTest, normal_counts)
{
    PostingListCounts counts;
    counts._bitLength = 15000;
    counts._numDocs = 15;
    write(counts);
    flush();
    auto act = read();
    EXPECT_EQ(counts, act);
    EXPECT_EQ(_wb.get_file_bit_size(), _dc.getReadOffset());
    EXPECT_EQ(27, _dc.getReadOffset());
}

TEST_F(PostingListCountsTest, huge_counts)
{
    PostingListCounts counts;
    counts._bitLength = 25_Mi;
    counts._numDocs = chunk_size + 10;
    auto& seg0 = counts._segments.emplace_back();
    seg0._numDocs = chunk_size;
    seg0._bitLength = 24_Mi;
    seg0._lastDoc = 1_Mi;
    auto& seg1 = counts._segments.emplace_back();
    seg1._numDocs = 10;
    seg1._bitLength = 1_Mi;
    seg1._lastDoc = 2_Mi;
    write(counts);
    flush();
    auto act = read();
    EXPECT_EQ(counts, act);
    EXPECT_EQ(_wb.get_file_bit_size(), _dc.getReadOffset());
    EXPECT_EQ(231, _dc.getReadOffset());
}

TEST_F(PostingListCountsTest, features_size_flush_counts)
{
    PostingListCounts counts;
    counts._bitLength = 100_Mi;
    counts._numDocs = 5;
    auto& seg0 = counts._segments.emplace_back();
    seg0._numDocs = 2;
    seg0._bitLength = 75_Mi;
    seg0._lastDoc = 25;
    auto& seg1 = counts._segments.emplace_back();
    seg1._numDocs = 3;
    seg1._bitLength = 25_Mi;
    seg1._lastDoc = 45;
    write(counts);
    flush();
    auto act = read();
    EXPECT_EQ(counts, act);
    EXPECT_EQ(_wb.get_file_bit_size(), _dc.getReadOffset());
    EXPECT_EQ(294, _dc.getReadOffset());
}

TEST_F(PostingListCountsTest, features_size_flush_marker_counts) {
    PostingListCounts counts;
    counts._bitLength = 25_Mi;
    counts._numDocs = search::diskindex::features_size_flush_marker;
    write(counts);
    flush();
    auto act = read();
    EXPECT_EQ(counts, act);
    EXPECT_EQ(_wb.get_file_bit_size(), _dc.getReadOffset());
    EXPECT_EQ(164, _dc.getReadOffset());
}

GTEST_MAIN_RUN_ALL_TESTS()
