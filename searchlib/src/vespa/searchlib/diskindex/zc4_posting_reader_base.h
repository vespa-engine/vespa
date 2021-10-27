// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "zc4_posting_params.h"
#include "zcbuf.h"
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/index/postinglistcounts.h>

namespace search::diskindex {

struct Zc4PostingHeader;

/*
 * Base class for reading posting lists that might have basic skip info.
 */
class Zc4PostingReaderBase
{

protected:
    using DecodeContext = bitcompression::DecodeContext64Base;

    class NoSkipBase {
    protected:
        ZcBuf _zc_buf;
        uint32_t _doc_id;
        uint32_t _doc_id_pos;
        uint64_t _features_pos;
    public:
        NoSkipBase();
        ~NoSkipBase();
        void setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id);
        void set_features_pos(uint64_t features_pos) { _features_pos = features_pos; }
        void read(bool decode_interleaved_features);
        void check_end(uint32_t last_doc_id);
        uint32_t get_doc_id()       const { return _doc_id; }
        uint32_t get_doc_id_pos()   const { return _doc_id_pos; }
        uint64_t get_features_pos() const { return _features_pos; }
        void set_doc_id(uint32_t doc_id)             { _doc_id = doc_id; }
    };
    class NoSkip : public NoSkipBase {
    protected:
        uint32_t _field_length;
        uint32_t _num_occs;
    public:
        NoSkip();
        ~NoSkip();
        void read(bool decode_interleaved_features);
        void check_not_end(uint32_t last_doc_id);
        uint32_t get_field_length() const { return _field_length; }
        uint32_t get_num_occs()     const { return _num_occs; }
        void set_field_length(uint32_t field_length) { _field_length = field_length; }
        void set_num_occs(uint32_t num_occs)         { _num_occs = num_occs; }
    };
    // Helper class for L1 skip info
    class L1Skip : public NoSkipBase {
    protected:
        uint32_t _l1_skip_pos;
    public:
        L1Skip();
        void setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id, uint32_t last_doc_id);
        void check(const NoSkipBase &no_skip, bool top_level, bool decode_features);
        void next_skip_entry();
        uint32_t get_l1_skip_pos() const { return _l1_skip_pos; }
    };
    class L2Skip : public L1Skip
    {
    protected:
        uint32_t _l2_skip_pos;
    public:
        L2Skip();
        void setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id, uint32_t last_doc_id);
        void check(const L1Skip &l1_skip, bool top_level, bool decode_features);
        uint32_t get_l2_skip_pos() const { return _l2_skip_pos; }
    };
    class L3Skip : public L2Skip
    {
    protected:
        uint32_t _l3_skip_pos;
    public:
        L3Skip();
        void setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id, uint32_t last_doc_id);
        void check(const L2Skip &l2_skip, bool top_level, bool decode_features);
        uint32_t get_l3_skip_pos() const { return _l3_skip_pos; }
    };
    class L4Skip : public L3Skip
    {
    public:
        L4Skip();
        void setup(DecodeContext &decode_context, uint32_t size, uint32_t doc_id, uint32_t last_doc_id);
        void check(const L3Skip &l3_skip, bool decode_features);
    };
    uint32_t _doc_id_k;
    uint32_t _num_docs;      // Documents in chunk or word
    search::ComprFileReadContext _readContext;
    bool _has_more;
    Zc4PostingParams _posting_params;
    uint32_t _last_doc_id;   // last document in chunk or word

    NoSkip _no_skip;
    L1Skip _l1_skip;
    L2Skip _l2_skip;
    L3Skip _l3_skip;
    L4Skip _l4_skip;

    uint64_t _numWords;     // Number of words in file
    uint32_t _chunkNo;      // Chunk number

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
