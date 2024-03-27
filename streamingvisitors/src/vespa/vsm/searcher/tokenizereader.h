// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/query/streaming/querynoderesultbase.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/fastlib/text/normwordfolder.h>

namespace vsm {

/**
 * Handles tokenization of utf8 input with on the fly normalization.
 * It handles Normalizing::NONE, Normalizing::LOWERCASE, and Normalizing::LOWERCASE_AND_FOLD
 */
class TokenizeReader {
public:
    using byte = search::byte;
    using Normalizing = search::Normalizing;
    TokenizeReader(const byte *p, uint32_t len, ucs4_t *q) noexcept
        : _p(p),
          _p_end(p + len),
          _q(q),
          _q_start(q)
    {}
    ucs4_t next() noexcept { return Fast_UnicodeUtil::GetUTF8Char(_p); }
    void normalize(ucs4_t c, Normalizing normalize_mode) {
        switch (normalize_mode) {
            case Normalizing::LOWERCASE:
                c = Fast_NormalizeWordFolder::lowercase(c);
                [[fallthrough]];
            case Normalizing::NONE:
                *_q++ = c;
                break;
            case Normalizing::LOWERCASE_AND_FOLD:
                fold(c);
                break;
        }
    }
    bool hasNext() const noexcept { return _p < _p_end; }
    const byte * p() const noexcept { return _p; }
    size_t complete() noexcept {
        *_q = 0;
        size_t token_len = _q - _q_start;
        _q = _q_start;
        return token_len;
    }
    template <bool exact_match>
    size_t tokenize_helper(Normalizing norm_mode);
    size_t tokenize(Normalizing norm_mode) { return tokenize_helper<false>(norm_mode); }
    size_t tokenize_exact_match(Normalizing norm_mode) { return tokenize_helper<true>(norm_mode); }
private:
    void fold(ucs4_t c);
    const byte *_p;
    const byte *_p_end;
    ucs4_t     *_q;
    ucs4_t     *_q_start;
};

}
