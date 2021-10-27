// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::bitcompression { class DecodeContext64Base; }

namespace search::diskindex {

struct Zc4PostingParams;

/*
 * Struct containing the decoded header for a word.
 */
struct Zc4PostingHeader {
    bool     _has_more;
    uint32_t _doc_id_k;
    uint32_t _num_docs;
    uint32_t _doc_ids_size;
    uint32_t _l1_skip_size;
    uint32_t _l2_skip_size;
    uint32_t _l3_skip_size;
    uint32_t _l4_skip_size;
    uint64_t _features_size;
    uint32_t _last_doc_id;

    Zc4PostingHeader();

    void
    read(bitcompression::DecodeContext64Base &decode_context, const Zc4PostingParams &params);
};

}
