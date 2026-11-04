// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_regex_search_helper.h"

#include <vespa/check_require.h>
#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search::attribute {

StringRegexSearchHelper::StringRegexSearchHelper(QueryTermUCS4& term, bool cased) : _regex() {
    CHECK(term.isRegex());
    _regex = cased ? vespalib::Regex::from_pattern(term.getTerm(), vespalib::Regex::Options::None)
                   : vespalib::Regex::from_pattern(term.getTerm(), vespalib::Regex::Options::IgnoreCase);
}

StringRegexSearchHelper::StringRegexSearchHelper(StringRegexSearchHelper&&) noexcept = default;

StringRegexSearchHelper::~StringRegexSearchHelper() = default;

bool StringRegexSearchHelper::is_match(const char* src) const noexcept {
    return get_regex().valid() && get_regex().partial_match(std::string_view(src));
}

} // namespace search::attribute
