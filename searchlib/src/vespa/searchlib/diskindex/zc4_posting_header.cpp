// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zc4_posting_header.h"
#include "zc4_posting_params.h"
#include <vespa/searchlib/bitcompression/compression.h>

namespace search::diskindex {

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

void
Zc4PostingHeader::read(bitcompression::DecodeContext64Base &decode_context, const Zc4PostingParams &params)
{
    using EC = bitcompression::FeatureEncodeContext<true>;
    _num_docs = decode_context.decode_exp_golomb(K_VALUE_ZCPOSTING_NUMDOCS) + 1;
    bool has_more = (_num_docs >= params._min_chunk_docs) ? (decode_context.readBits(1) != 0) : false;
    _doc_id_k = params._dynamic_k ? EC::calcDocIdK((_has_more || has_more) ? 1 : _num_docs, params._doc_id_limit) : K_VALUE_ZCPOSTING_LASTDOCID;
    if (_num_docs < params._min_skip_docs && !_has_more) {
        _doc_ids_size = 0;
        _l1_skip_size = 0;
        _l2_skip_size = 0;
        _l3_skip_size = 0;
        _l4_skip_size = 0;
        _features_size = 0;
        _last_doc_id = 0;
    } else {
        _doc_ids_size = decode_context.decode_exp_golomb(K_VALUE_ZCPOSTING_DOCIDSSIZE) + 1;
        _l1_skip_size = decode_context.decode_exp_golomb(K_VALUE_ZCPOSTING_L1SKIPSIZE);
        _l2_skip_size = (_l1_skip_size != 0) ? decode_context.decode_exp_golomb(K_VALUE_ZCPOSTING_L2SKIPSIZE) : 0;
        _l3_skip_size = (_l2_skip_size != 0) ? decode_context.decode_exp_golomb(K_VALUE_ZCPOSTING_L3SKIPSIZE) : 0;
        _l4_skip_size = (_l3_skip_size != 0) ? decode_context.decode_exp_golomb(K_VALUE_ZCPOSTING_L4SKIPSIZE) : 0;
        _features_size = params._encode_features ? decode_context.decode_exp_golomb(K_VALUE_ZCPOSTING_FEATURESSIZE) : 0;
        _last_doc_id = params._doc_id_limit - 1 - decode_context.decode_exp_golomb(_doc_id_k);
        decode_context.align(8);
    }
    _has_more = has_more;
}

}
