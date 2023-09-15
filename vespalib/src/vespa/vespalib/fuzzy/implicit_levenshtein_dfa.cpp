// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "implicit_levenshtein_dfa.hpp"
#include "unicode_utils.h"
#include <cassert>

namespace vespalib::fuzzy {

template <typename Traits>
void ImplicitLevenshteinDfa<Traits>::precompute_utf8_target_with_offsets() {
    _target_utf8_char_offsets.reserve(_u32_str_buf.size());
    // Important: the precomputed UTF-8 target is based on the potentially case-normalized
    // target string, ensuring that we don't emit raw target chars for uncased successors.
    for (uint32_t u32ch : _u32_str_buf) {
        _target_utf8_char_offsets.emplace_back(static_cast<uint32_t>(_target_as_utf8.size()));
        append_utf32_char(_target_as_utf8, u32ch);
    }
    assert(_target_as_utf8.size() < UINT32_MAX);
}

template class ImplicitLevenshteinDfa<FixedMaxEditDistanceTraits<1>>;
template class ImplicitLevenshteinDfa<FixedMaxEditDistanceTraits<2>>;

}
