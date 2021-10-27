// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zc4_posting_writer_base.h"

namespace search::index { class DocIdAndFeatures; }

namespace search::diskindex {

/*
 * Class used to write posting lists of type "Zc.4" and "Zc.5" (dynamic k).
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
class Zc4PostingWriter : public Zc4PostingWriterBase
{
    using EncodeContext = bitcompression::FeatureEncodeContext<bigEndian>;

    EncodeContext _encode_context;
    // Buffer up features in memory
    EncodeContext *_encode_features;
public:
    Zc4PostingWriter(const Zc4PostingWriter &) = delete;
    Zc4PostingWriter(Zc4PostingWriter &&) = delete;
    Zc4PostingWriter &operator=(const Zc4PostingWriter &) = delete;
    Zc4PostingWriter &operator=(Zc4PostingWriter &&) = delete;
    Zc4PostingWriter(index::PostingListCounts &counts);
    ~Zc4PostingWriter();

    void reset_chunk();
    void flush_word_with_skip(bool hasMore);
    void flush_word_no_skip();
    void flush_word();
    void write_docid_and_features(const index::DocIdAndFeatures &features);
    void set_encode_features(EncodeContext *encode_features);
    void on_open();
    void on_close();

    EncodeContext &get_encode_features() { return *_encode_features; }
    EncodeContext &get_encode_context() { return _encode_context; }
};

extern template class Zc4PostingWriter<false>;
extern template class Zc4PostingWriter<true>;

}
