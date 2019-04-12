// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_header.h"
#include "zc4_posting_params.h"
#include <vespa/searchlib/bitcompression/compression.h>

namespace search::diskindex
{

Zc4PostingHeader::Zc4PostingHeader()
    : _has_more(false),
      _doc_id_k(K_VALUE_ZCPOSTING_LASTDOCID),
      _num_docs(0u),
      _doc_ids_size(0u),
      _l1_skip_size(0u),
      _l2_skip_size(0u),
      _l3_skip_size(0u),
      _l4_skip_size(0u),
      _features_size(0u),
      _last_doc_id(0)
{
}

template <bool bigEndian>
void
Zc4PostingHeader::read(bitcompression::DecodeContext64Base &decode_context, const Zc4PostingParams &params)
{
    using EC = bitcompression::FeatureEncodeContext<bigEndian>;
    UC64_DECODECONTEXT_CONSTRUCTOR(o, decode_context._);
    uint32_t length;
    uint64_t val64;

    UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_NUMDOCS, EC);
    _num_docs = static_cast<uint32_t>(val64) + 1;
    bool has_more = false;
    if (__builtin_expect(_num_docs >= params._min_chunk_docs, false)) {
        if (bigEndian) {
            has_more = static_cast<int64_t>(oVal) < 0;
            oVal <<= 1;
            length = 1;
        } else {
            has_more = (oVal & 1) != 0;
            oVal >>= 1;
            length = 1;
        }
        UC64_READBITS_NS(o, EC);
    }
    if (params._dynamic_k) {
        _doc_id_k = EC::calcDocIdK((_has_more || has_more) ? 1 : _num_docs, params._doc_id_limit);
    } else {
        _doc_id_k = K_VALUE_ZCPOSTING_LASTDOCID;
    }
    if (_num_docs < params._min_skip_docs && !_has_more) {
        _doc_ids_size = 0;
        _l1_skip_size = 0;
        _l2_skip_size = 0;
        _l3_skip_size = 0;
        _l4_skip_size = 0;
        _features_size = 0;
        _last_doc_id = 0;
    } else {
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_DOCIDSSIZE, EC);
        _doc_ids_size = val64 + 1;
        UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L1SKIPSIZE, EC);
        _l1_skip_size = val64;
        if (_l1_skip_size != 0) {
            UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L2SKIPSIZE, EC);
            _l2_skip_size = val64;
        }
        if (_l2_skip_size != 0) {
            UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L3SKIPSIZE, EC);
            _l3_skip_size = val64;
        }
        if (_l3_skip_size != 0) {
            UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_L4SKIPSIZE, EC);
            _l4_skip_size = val64;
        }
        if (params._encode_features) {
            UC64_DECODEEXPGOLOMB_NS(o, K_VALUE_ZCPOSTING_FEATURESSIZE, EC);
            _features_size = val64;
        } else {
            _features_size = 0;
        }
        UC64_DECODEEXPGOLOMB_NS(o, _doc_id_k, EC);
        _last_doc_id = params._doc_id_limit - 1 - val64;
        uint64_t bytePad = oPreRead & 7;
        if (bytePad > 0) {
            length = bytePad;
            UC64_READBITS_NS(o, EC);
        }
    }
    UC64_DECODECONTEXT_STORE(o, decode_context._);
    _has_more = has_more;
}

template
void
Zc4PostingHeader::read<false>(bitcompression::DecodeContext64Base &decode_context, const Zc4PostingParams &params);

template
void
Zc4PostingHeader::read<true>(bitcompression::DecodeContext64Base &decode_context, const Zc4PostingParams &params);


}
