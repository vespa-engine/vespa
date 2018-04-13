// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/countcompression.h>
#include <vespa/searchlib/bitcompression/pagedict4.h>
#include <vespa/searchlib/test/diskindex/threelevelcountbuffers.h>
#include <vespa/searchlib/test/diskindex/pagedict4_mem_writer.h>
#include <vespa/searchlib/test/diskindex/pagedict4_mem_seq_reader.h>
#include <vespa/searchlib/index/postinglistcounts.h>

#include <vespa/log/log.h>
LOG_SETUP("pagedict4_hugeword_cornercase_test");

using search::index::PostingListCounts;
using search::ComprFileWriteContext;

constexpr uint32_t minChunkDocs = 262144;
constexpr uint32_t numWordIds = 65536;

struct BitBuffer
{
    using EC = search::bitcompression::PostingListCountFileEncodeContext;

    EC encodeCtx;
    ComprFileWriteContext writeCtx;

    BitBuffer()
        : encodeCtx(),
          writeCtx(encodeCtx)
    {
        encodeCtx._minChunkDocs = minChunkDocs;
        encodeCtx._numWordIds = numWordIds;
        writeCtx.allocComprBuf();
        encodeCtx.setWriteContext(&writeCtx);
        encodeCtx.setupWrite(writeCtx);
        assert(encodeCtx.getWriteOffset() == 0);
    }

    void clear() { encodeCtx.setupWrite(writeCtx); }

    uint64_t getSize(const PostingListCounts &counts)
    {
        clear();
        encodeCtx.writeCounts(counts);
        return encodeCtx.getWriteOffset();
    }

    ~BitBuffer() = default;
};

void addSegment(PostingListCounts &counts)
{
    PostingListCounts::Segment lastseg = counts._segments.back();
    PostingListCounts::Segment fillseg;
    fillseg._bitLength = 4000000;
    fillseg._numDocs = minChunkDocs;
    fillseg._lastDoc = minChunkDocs;
    counts._bitLength += fillseg._bitLength;
    counts._numDocs += fillseg._numDocs;
    counts._segments.back() = fillseg;
    uint32_t lastDoc = counts._segments.size() * fillseg._numDocs;
    counts._segments.back()._lastDoc = lastDoc;
    counts._segments.push_back(lastseg);
    counts._segments.back()._lastDoc = lastDoc + lastseg._numDocs;
}

PostingListCounts makeBaseCounts()
{
    PostingListCounts counts;
    PostingListCounts::Segment lastseg;
    lastseg._bitLength = 100;
    lastseg._numDocs = 10;
    lastseg._lastDoc = 10;
    counts._bitLength = lastseg._bitLength;
    counts._numDocs = lastseg._numDocs;
    counts._segments.push_back(lastseg);
    addSegment(counts);
    return counts;
}

PostingListCounts makeSegmentedCounts(uint32_t segments)
{
    PostingListCounts counts = makeBaseCounts();
    while (counts._segments.size() < segments) {
        addSegment(counts);
    }
    return counts;
}

uint32_t
calcSegments(uint32_t maxLen)
{
    BitBuffer bb;
    PostingListCounts counts = makeBaseCounts();
    uint32_t len = bb.getSize(counts);
    unsigned int i = 0;
    while (len <= maxLen) {
        addSegment(counts);
        ++i;
        len = bb.getSize(counts);
    }
    return counts._segments.size() - 1;
}

/*
 * Calculate posting list counts that compresses to wantLen bits.
 */
PostingListCounts makeCounts(uint32_t wantLen)
{
    BitBuffer bb;
    uint32_t segments = calcSegments(wantLen);
    PostingListCounts counts = makeSegmentedCounts(segments);
    PostingListCounts counts2 = makeSegmentedCounts(segments - 1);
    uint32_t len = bb.getSize(counts);
    uint32_t len2 = bb.getSize(counts2);
    for (uint32_t i = 1; i + 2 < counts._segments.size(); ++i) {
        counts._bitLength += counts._segments[0]._bitLength;
        counts._segments[i]._bitLength += counts._segments[0]._bitLength;
        counts2._bitLength += counts2._segments[0]._bitLength;
        counts2._segments[i]._bitLength += counts2._segments[0]._bitLength;
        len = bb.getSize(counts);
        len2 = bb.getSize(counts2);
        if (len == wantLen) {
            return counts;
        }
        if (len2 == wantLen) {
            return counts2;
        }
    }
    LOG(info, "Could not calculate counts with wanted compressed length");
    abort();
}

using StartOffset = search::bitcompression::PageDict4StartOffset;
using Writer = search::diskindex::test::PageDict4MemWriter;
using SeqReader = search::diskindex::test::PageDict4MemSeqReader;

/*
 * Test corner case where a dictionary page has a single word, and the
 * page header and compressed counts completely fills the page.
 */
void testPageSizedCounts()
{
    uint32_t pageBitSize = 32768;
    uint32_t startBits = 15 * 3 + 12;

    uint32_t ssPad = 64;
    uint32_t spPad = 64;
    uint32_t pPad = 64;
    Writer w(minChunkDocs, numWordIds, ssPad, spPad, pPad);
    PostingListCounts baseCounts = makeBaseCounts();
    PostingListCounts largeCounts = makeCounts(pageBitSize - startBits);
    w.addCounts("a", baseCounts);
    w.addCounts("b", baseCounts);
    w.addCounts("c", largeCounts);
    w.addCounts("d", baseCounts);
    w.addCounts("e", baseCounts);
    w.flush();

    SeqReader r(minChunkDocs, numWordIds, w._buffers);

    uint64_t checkWordNum = 0;
    PostingListCounts counts;
    for (uint64_t wordNum = 1; wordNum < 7; ++wordNum) {
        vespalib::string word;
        counts.clear();
        r.readCounts(word, checkWordNum, counts);
        if (wordNum < 6) {
            EXPECT_EQUAL(checkWordNum, wordNum);
            if (wordNum == 3) {
                EXPECT_TRUE(counts == largeCounts);
            } else {
                EXPECT_TRUE(counts == baseCounts);
            }
        } else {
            EXPECT_GREATER(checkWordNum, 100u);
        }
    }
}



TEST("require that counts exactly filling dictionary page works")
{
    testPageSizedCounts();
}


TEST_MAIN() { TEST_RUN_ALL(); }
