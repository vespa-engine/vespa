// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compression.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <limits>

#define K_VALUE_COUNTFILE_POSOCCBITS 6

namespace search::bitcompression {

class PostingListCountFileDecodeContext : public FeatureDecodeContext<true>
{
public:
    typedef FeatureDecodeContext<true> ParentClass;
    typedef index::PostingListCounts PostingListCounts;
    uint32_t _avgBitsPerDoc;    // Average number of bits per document
    uint32_t _minChunkDocs; // Minimum number of documents for chunking
    uint32_t _docIdLimit;   // Limit for document ids (docId < docIdLimit)
    uint64_t _numWordIds;   // Number of words in dictionary

    PostingListCountFileDecodeContext()
        : ParentClass(),
          _avgBitsPerDoc(10),
          _minChunkDocs(262144),
          _docIdLimit(10000000),
          _numWordIds(0)
    {
    }

    void readCounts(PostingListCounts &counts);

    static uint64_t noWordNum() {
        return std::numeric_limits<uint64_t>::max();
    }

    void copyParams(const PostingListCountFileDecodeContext &rhs);
};


class PostingListCountFileEncodeContext : public FeatureEncodeContext<true>
{
public:
    typedef FeatureEncodeContext<true> ParentClass;
    typedef index::PostingListCounts PostingListCounts;
    uint32_t _avgBitsPerDoc;    // Average number of bits per document
    uint32_t _minChunkDocs; // Minimum number of documents for chunking
    uint32_t _docIdLimit;   // Limit for document ids (docId < docIdLimit)
    uint64_t _numWordIds;   // Number of words in dictionary

    PostingListCountFileEncodeContext()
        : ParentClass(),
          _avgBitsPerDoc(10),
          _minChunkDocs(262144),
          _docIdLimit(10000000),
          _numWordIds(0)
    {
    }

    void writeCounts(const PostingListCounts &counts);

    static uint64_t noWordNum() {
        return std::numeric_limits<uint64_t>::max();
    }

    void copyParams(const PostingListCountFileEncodeContext &rhs);
};

}
