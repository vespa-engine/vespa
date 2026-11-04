// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/regex/regex.h>

namespace search {
class QueryTermUCS4;
}

namespace search::attribute {

/**
 * Helper class for search context when scanning string fields via regex.
 */
class StringRegexSearchHelper {
public:
    StringRegexSearchHelper(QueryTermUCS4& term, bool cased);
    StringRegexSearchHelper(StringRegexSearchHelper&&) noexcept;
    StringRegexSearchHelper(const StringRegexSearchHelper&) = delete;
    StringRegexSearchHelper& operator=(const StringRegexSearchHelper&) = delete;
    ~StringRegexSearchHelper();
    bool is_match(const char* src) const noexcept;
    const vespalib::Regex& get_regex() const noexcept { return _regex; }

private:
    vespalib::Regex _regex;
};

} // namespace search::attribute
