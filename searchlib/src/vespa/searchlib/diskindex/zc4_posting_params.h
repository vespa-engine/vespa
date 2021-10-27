// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::diskindex {

/*
 * Struct containing parameters for posting list.
 */
struct Zc4PostingParams {
    uint32_t _min_skip_docs;
    uint32_t _min_chunk_docs;
    uint32_t _doc_id_limit;
    bool     _dynamic_k;
    bool     _encode_features;
    bool     _encode_interleaved_features;

    Zc4PostingParams(uint32_t min_skip_docs, uint32_t min_chunk_docs, uint32_t doc_id_limit, bool dynamic_k, bool encode_features, bool encode_interleaved_features)
        : _min_skip_docs(min_skip_docs),
          _min_chunk_docs(min_chunk_docs),
          _doc_id_limit(doc_id_limit),
          _dynamic_k(dynamic_k),
          _encode_features(encode_features),
          _encode_interleaved_features(encode_interleaved_features)
    {
    }
};

}
