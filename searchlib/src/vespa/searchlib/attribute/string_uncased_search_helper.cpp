// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_uncased_search_helper.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>

namespace search::attribute {

StringUncasedSearchHelper::StringUncasedSearchHelper(const QueryTermUCS4& term)
    : _ucs4(term.asUcs4()), _is_prefix(term.isPrefix()) {
}

StringUncasedSearchHelper::StringUncasedSearchHelper(StringUncasedSearchHelper&&) noexcept = default;

StringUncasedSearchHelper::~StringUncasedSearchHelper() = default;

bool StringUncasedSearchHelper::is_match(const char* src) const noexcept {
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
    return (_ucs4[j] == 0 && (val == 0 || _is_prefix));
}

} // namespace search::attribute
