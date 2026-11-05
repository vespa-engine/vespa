// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_search_helper.h"

#include "i_enum_store_dictionary.h"

#include <vespa/check_require.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>

namespace search::attribute {

StringSearchHelper::StringSearchHelper(QueryTermUCS4& term, bool cased)
    : _ucs4(), _term(), _term_len(), _is_prefix(term.isPrefix()), _is_cased(cased) {
    // Fuzzy and regex matching is handled in separate helper classes
    CHECK(!term.isFuzzy() && !term.isRegex());
    if (is_cased()) {
        _term = term.getTerm();
        _term_len = strlen(_term);
    } else {
        _ucs4 = term.asUcs4();
    }
}

StringSearchHelper::StringSearchHelper(StringSearchHelper&&) noexcept = default;

StringSearchHelper::~StringSearchHelper() = default;

bool StringSearchHelper::is_match(const char* src) const noexcept {
    if (__builtin_expect(is_cased(), false)) {
        int res = strncmp(_term, src, _term_len);
        return (res == 0) && (src[_term_len] == 0 || is_prefix());
    }
    vespalib::Utf8ReaderForZTS u8reader(src);
    uint32_t                   j = 0;
    uint32_t                   val;
    for (;; ++j) {
        val = u8reader.getChar();
        val = vespalib::LowerCase::convert(val);
        if (_ucs4[j] == 0 || _ucs4[j] != val) {
            break;
        }
    }
    return (_ucs4[j] == 0 && (val == 0 || is_prefix()));
}

} // namespace search::attribute
