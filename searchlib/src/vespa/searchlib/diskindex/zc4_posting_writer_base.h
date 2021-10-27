// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zcbuf.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vector>

namespace search::index {
class PostingListCounts;
class PostingListParams;
}

namespace search::diskindex {

/*
 * Base class for writing posting lists that might have basic skip info.
 */
class Zc4PostingWriterBase
{
public:
    struct DocIdAndFeatureSize {
        uint32_t _doc_id;
        uint32_t _field_length;
        uint32_t _num_occs;
        uint32_t _features_size;
        DocIdAndFeatureSize(uint32_t doc_id, uint32_t field_length, uint32_t num_occs, uint32_t features_size) noexcept
            : _doc_id(doc_id),
              _field_length(field_length),
              _num_occs(num_occs),
              _features_size(features_size)
        {
        }
    };
protected:
    uint32_t _minChunkDocs; // # of documents needed for chunking
    uint32_t _minSkipDocs;  // # of documents needed for skipping
    uint32_t _docIdLimit;   // Limit for document ids (docId < docIdLimit)

    // Unpacked document ids for word and feature sizes
    std::vector<DocIdAndFeatureSize> _docIds;

    uint64_t _featureOffset;        // Bit offset of next feature
    uint64_t _writePos; // Bit position for start of current word
    bool _dynamicK;     // Caclulate EG compression parameters ?
    bool _encode_interleaved_features;
    ZcBuf _zcDocIds;    // Document id deltas
    ZcBuf _l1Skip;      // L1 skip info
    ZcBuf _l2Skip;      // L2 skip info
    ZcBuf _l3Skip;      // L3 skip info
    ZcBuf _l4Skip;      // L4 skip info

    uint64_t _numWords; // Number of words in file
    index::PostingListCounts &_counts;
    search::ComprFileWriteContext _writeContext;
    search::ComprFileWriteContext _featureWriteContext;

    Zc4PostingWriterBase(const Zc4PostingWriterBase &) = delete;
    Zc4PostingWriterBase(Zc4PostingWriterBase &&) = delete;
    Zc4PostingWriterBase &operator=(const Zc4PostingWriterBase &) = delete;
    Zc4PostingWriterBase &operator=(Zc4PostingWriterBase &&) = delete;
    Zc4PostingWriterBase(index::PostingListCounts &counts);
    ~Zc4PostingWriterBase();
    void calc_skip_info(bool encode_features);
    void clear_skip_info();

public:
    ComprFileWriteContext &get_write_context() { return _writeContext; }
    ComprFileWriteContext &get_feature_write_context() { return _featureWriteContext; }
    uint32_t get_min_chunk_docs() const { return _minChunkDocs; }
    uint32_t get_min_skip_docs() const { return _minSkipDocs; }
    uint32_t get_docid_limit() const { return _docIdLimit; }
    uint64_t get_num_words() const { return _numWords; }
    bool get_dynamic_k() const { return _dynamicK; }
    bool get_encode_interleaved_features() const { return _encode_interleaved_features; }
    void set_dynamic_k(bool dynamicK) { _dynamicK = dynamicK; }
    void set_encode_interleaved_features(bool encode_interleaved_features) { _encode_interleaved_features = encode_interleaved_features; }
    void set_posting_list_params(const index::PostingListParams &params);
};

}
