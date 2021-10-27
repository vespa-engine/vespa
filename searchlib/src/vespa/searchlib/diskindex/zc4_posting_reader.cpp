// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_reader.h"
#include "zc4_posting_header.h"
#include <vespa/searchlib/index/docidandfeatures.h>

namespace search::diskindex {

using index::PostingListCounts;
using index::DocIdAndFeatures;
using bitcompression::FeatureEncodeContext;


template <bool bigEndian>
Zc4PostingReader<bigEndian>::Zc4PostingReader(bool dynamic_k)
    : Zc4PostingReaderBase(dynamic_k),
      _decodeContext(nullptr)
{
}

template <bool bigEndian>
Zc4PostingReader<bigEndian>::~Zc4PostingReader()
{
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::read_doc_id_and_features(DocIdAndFeatures &features)
{
    if (_residue == 0) {
        if (_has_more) {
            read_word_start();
            assert(_residue != 0);
        } else {
            // Don't read past end of posting list.
            features.clear(static_cast<uint32_t>(-1));
            return;
        }
    }
    if (_last_doc_id > 0) {
        // Split docid & features.
        read_common_word_doc_id(*_decodeContext);
    } else {
        // Interleaves docid & features
        using EC = FeatureEncodeContext<bigEndian>;
        DecodeContext &d = *_decodeContext;
        uint32_t length;
        uint64_t val64;
        UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
        
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, _doc_id_k, EC);
        _no_skip.set_doc_id(_no_skip.get_doc_id() + 1 + val64);
        if (_posting_params._encode_interleaved_features) {
            if (__builtin_expect(oCompr >= d._valE, false)) {
                UC64_DECODECONTEXT_STORE(o, d._);
                _readContext.readComprBuffer();
                UC64_DECODECONTEXT_LOAD(o, d._);
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_ZCPOSTING_FIELD_LENGTH, EC);
            _no_skip.set_field_length(val64 + 1);
            if (__builtin_expect(oCompr >= d._valE, false)) {
                UC64_DECODECONTEXT_STORE(o, d._);
                _readContext.readComprBuffer();
                UC64_DECODECONTEXT_LOAD(o, d._);
            }
            UC64_DECODEEXPGOLOMB_SMALL_NS(o, K_VALUE_ZCPOSTING_NUM_OCCS, EC);
            _no_skip.set_num_occs(val64 + 1);
        }
        UC64_DECODECONTEXT_STORE(o, d._);
        if (__builtin_expect(oCompr >= d._valE, false)) {
            _readContext.readComprBuffer();
        }
    }
    features.set_doc_id(_no_skip.get_doc_id());
    if (_posting_params._encode_features) {
        if (_posting_params._encode_interleaved_features) {
            features.set_field_length(_no_skip.get_field_length());
            features.set_num_occs(_no_skip.get_num_occs());
        }
        _decodeContext->readFeatures(features);
    }
    --_residue;
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::read_word_start()
{
    Zc4PostingReaderBase::read_word_start(*_decodeContext);
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::set_counts(const PostingListCounts &counts)
{
    Zc4PostingReaderBase::set_counts(*_decodeContext, counts);
}

template <bool bigEndian>
void
Zc4PostingReader<bigEndian>::set_decode_features(DecodeContext *decode_features)
{
    _decodeContext = decode_features;
    _decodeContext->setReadContext(&_readContext);
    _readContext.setDecodeContext(_decodeContext);
}

template class Zc4PostingReader<false>;
template class Zc4PostingReader<true>;

}
