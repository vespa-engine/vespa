// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    if (_residue == 0 && !_has_more) {
        // Don't read past end of posting list.
        features.clear(static_cast<uint32_t>(-1));
        return;
    }
    if (_last_doc_id > 0) {
        read_common_word_doc_id(*_decodeContext);
    } else {
        // Interleaves docid & features
        using EC = FeatureEncodeContext<bigEndian>;
        DecodeContext &d = *_decodeContext;
        uint32_t length;
        uint64_t val64;
        UC64_DECODECONTEXT_CONSTRUCTOR(o, d._);
        
        UC64_DECODEEXPGOLOMB_SMALL_NS(o, _doc_id_k, EC);
        uint32_t docId = _prev_doc_id + 1 + val64;
        _prev_doc_id = docId;
        UC64_DECODECONTEXT_STORE(o, d._);
        if (__builtin_expect(oCompr >= d._valE, false)) {
            _readContext.readComprBuffer();
        }
    }
    features.set_doc_id(_prev_doc_id);
    if (_posting_params._encode_features) {
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
