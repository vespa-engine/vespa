// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zc4_posting_writer.h"
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/fastos/file.h>
#include "zc4_posting_params.h"

namespace search::index {
    class PostingListCountFileSeqRead;
}

namespace search::diskindex {

/*
 * Class used to read posting lists of type "Zc.4" and "Zc.5" (dynamic k).
 *
 * Common words have docid deltas and skip info separate from
 * features.
 * 
 * Rare words do not have skip info, and docid deltas and features are
 * interleaved.
 */
template <bool bigEndian>
class Zc4PostingReader
{

protected:
    using DecodeContext = bitcompression::FeatureDecodeContext<bigEndian>;

    DecodeContext *_decodeContext;
    uint32_t _docIdK;
    uint32_t _prevDocId;    // Previous document id
    uint32_t _numDocs;      // Documents in chunk or word
    search::ComprFileReadContext _readContext;
    bool _has_more;
    Zc4PostingParams _posting_params;
    uint32_t _lastDocId;    // last document in chunk or word

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
    uint64_t _featuresSize;
    index::PostingListCounts _counts;

    uint32_t _residue;            // Number of unread documents after word header
    void read_common_word_doc_id_and_features(index::DocIdAndFeatures &features);
    void read_word_start_with_skip();
    void read_word_start();
public:
    Zc4PostingReader(bool dynamic_k);
    Zc4PostingReader(const Zc4PostingReader &) = delete;
    Zc4PostingReader(Zc4PostingReader &&) = delete;
    Zc4PostingReader &operator=(const Zc4PostingReader &) = delete;
    Zc4PostingReader &operator=(Zc4PostingReader &&) = delete;
    ~Zc4PostingReader();
    void read_doc_id_and_features(index::DocIdAndFeatures &features);
    void set_counts(const index::PostingListCounts &counts);
    void set_decode_features(DecodeContext *decode_features);
    DecodeContext &get_decode_features() const { return *_decodeContext; }
    ComprFileReadContext &get_read_context() { return _readContext; }
    Zc4PostingParams &get_posting_params() { return _posting_params; }
};

extern template class Zc4PostingReader<false>;
extern template class Zc4PostingReader<true>;

}
