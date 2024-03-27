// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tokenizereader.h"

namespace vsm {

namespace {

template <bool exact_match> inline bool is_word_char(ucs4_t c);

template <>
inline bool is_word_char<false>(ucs4_t c) { return Fast_UnicodeUtil::IsWordChar(c); }

// All characters are treated as word characters for exact match
template <>
inline constexpr bool is_word_char<true>(ucs4_t) { return true; }

}

void
TokenizeReader::fold(ucs4_t c) {
    const char *repl = Fast_NormalizeWordFolder::ReplacementString(c);
    if (repl != nullptr) {
        size_t repllen = strlen(repl);
        if (repllen > 0) {
            _q = Fast_UnicodeUtil::ucs4copy(_q,repl);
        }
    } else {
        c = Fast_NormalizeWordFolder::lowercase_and_fold(c);
        *_q++ = c;
    }
}

template <bool exact_match>
size_t
TokenizeReader::tokenize_helper(Normalizing norm_mode)
{
    ucs4_t c(0);
    while (hasNext()) {
        if (is_word_char<exact_match>(c = next())) {
            normalize(c, norm_mode);
            while (hasNext() && is_word_char<exact_match>(c = next())) {
                normalize(c, norm_mode);
            }
            break;
        }
    }
    return complete();
}

template size_t TokenizeReader::tokenize_helper<false>(Normalizing);
template size_t TokenizeReader::tokenize_helper<true>(Normalizing);

}
