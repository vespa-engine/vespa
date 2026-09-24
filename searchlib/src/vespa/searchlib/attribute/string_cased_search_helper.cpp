// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_cased_search_helper.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

#include <cstring>

namespace search::attribute {

StringCasedSearchHelper::StringCasedSearchHelper(const QueryTermUCS4& term)
    : _term(term.getTerm()), _term_len(strlen(_term)), _is_prefix(term.isPrefix()) {
}

StringCasedSearchHelper::StringCasedSearchHelper(StringCasedSearchHelper&&) noexcept = default;

StringCasedSearchHelper::~StringCasedSearchHelper() = default;

bool StringCasedSearchHelper::is_match(const char* src) const noexcept {
    int res = strncmp(_term, src, _term_len);
    return (res == 0) && (src[_term_len] == 0 || _is_prefix);
}

} // namespace search::attribute
