// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zc4_posting_reader_base.h"

namespace search::diskindex {

/*
 * Class used to read posting lists of type "Zc.4" and "Zc.5" (dynamic k).
 *
 * Common words have docid deltas and skip info separate from
 * features. If "cheap" features are enabled then they are interleaved
 * with docid deltas for quick access during sequential scan while the
 * full features still remains separate.
 * 
 * Rare words do not have skip info, and docid deltas and features are
 * interleaved.
 */
template <bool bigEndian>
class Zc4PostingReader : public Zc4PostingReaderBase
{

protected:
    using DecodeContext = bitcompression::FeatureDecodeContext<bigEndian>;

    DecodeContext *_decodeContext;
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
};

extern template class Zc4PostingReader<false>;
extern template class Zc4PostingReader<true>;

}
