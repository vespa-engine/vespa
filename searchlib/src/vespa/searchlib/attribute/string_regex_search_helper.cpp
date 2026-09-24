// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_regex_search_helper.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search::attribute {

StringRegexSearchHelper::StringRegexSearchHelper(const QueryTermUCS4& term, bool cased)
    : _regex(vespalib::Regex::from_pattern(term.getTerm(), cased ? vespalib::Regex::Options::None
                                                                 : vespalib::Regex::Options::IgnoreCase)) {
}

StringRegexSearchHelper::StringRegexSearchHelper(StringRegexSearchHelper&&) noexcept = default;

StringRegexSearchHelper::~StringRegexSearchHelper() = default;

} // namespace search::attribute
