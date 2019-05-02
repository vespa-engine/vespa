// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zc4_posting_params.h"
#include "zcbuf.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/index/postinglistcounts.h>

namespace search::diskindex {

class Zc4PostingHeader;

/*
 * Base class for reading posting lists that might have basic skip info.
 */
class Zc4PostingReaderBase
{

protected:
    uint32_t _doc_id_k;
    uint32_t _prev_doc_id;   // Previous document id
    uint32_t _num_docs;      // Documents in chunk or word
    search::ComprFileReadContext _readContext;
    bool _has_more;
    Zc4PostingParams _posting_params;
    uint32_t _last_doc_id;   // last document in chunk or word

    ZcBuf _zcDocIds;    // Document id deltas
    ZcBuf _l1Skip;      // L1 skip info
    ZcBuf _l2Skip;      // L2 skip info
    ZcBuf _l3Skip;      // L3 skip info
    ZcBuf _l4Skip;      // L4 skip info

    uint64_t _numWords;     // Number of words in file
    uint32_t _chunkNo;      // Chunk number

    // Variables for validating skip information while reading
    uint32_t _l1SkipDocId;
    uint32_t _l1SkipDocIdPos;
    uint64_t _l1SkipFeaturesPos;
    uint32_t _l2SkipDocId;
    uint32_t _l2SkipDocIdPos;
    uint32_t _l2SkipL1SkipPos;
    uint64_t _l2SkipFeaturesPos;
    uint32_t _l3SkipDocId;
    uint32_t _l3SkipDocIdPos;
    uint32_t _l3SkipL1SkipPos;
    uint32_t _l3SkipL2SkipPos;
    uint64_t _l3SkipFeaturesPos;
    uint32_t _l4SkipDocId;
    uint32_t _l4SkipDocIdPos;
    uint32_t _l4SkipL1SkipPos;
    uint32_t _l4SkipL2SkipPos;
    uint32_t _l4SkipL3SkipPos;
    uint64_t _l4SkipFeaturesPos;

    // Variable for validating chunk information while reading
    uint64_t _features_size;
    index::PostingListCounts _counts;

    uint32_t _residue;            // Number of unread documents after word header
    void read_common_word_doc_id(bitcompression::DecodeContext64Base &decode_context);
    void read_word_start_with_skip(bitcompression::DecodeContext64Base &decode_context, const Zc4PostingHeader &header);
    void read_word_start(bitcompression::DecodeContext64Base &decode_context);
public:
    Zc4PostingReaderBase(bool dynamic_k);
    Zc4PostingReaderBase(const Zc4PostingReaderBase &) = delete;
    Zc4PostingReaderBase(Zc4PostingReaderBase &&) = delete;
    Zc4PostingReaderBase &operator=(const Zc4PostingReaderBase &) = delete;
    Zc4PostingReaderBase &operator=(Zc4PostingReaderBase &&) = delete;
    ~Zc4PostingReaderBase();
    void read_doc_id_and_features(index::DocIdAndFeatures &features);
    void set_counts(bitcompression::DecodeContext64Base &decode_context, const index::PostingListCounts &counts);
    ComprFileReadContext &get_read_context() { return _readContext; }
    Zc4PostingParams &get_posting_params() { return _posting_params; }
};

}
