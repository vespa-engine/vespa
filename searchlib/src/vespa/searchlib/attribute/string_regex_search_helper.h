// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/regex/regex.h>

namespace search {
class QueryTermUCS4;
}

namespace search::attribute {

/**
 * Helper class for StringRegexMatcher that implements the actual matching logic
 * for (cased or uncased) regex matching.
 * Separate class from StringRegexMatcher for unit testing.
 */
class StringRegexSearchHelper {
private:
    vespalib::Regex _regex;

public:
    StringRegexSearchHelper(const QueryTermUCS4& term, bool cased);
    StringRegexSearchHelper(StringRegexSearchHelper&&) noexcept;
    StringRegexSearchHelper(const StringRegexSearchHelper&) = delete;
    StringRegexSearchHelper& operator=(const StringRegexSearchHelper&) = delete;
    ~StringRegexSearchHelper();

    [[nodiscard]] bool is_match(const char* src) const noexcept {
        return _regex.valid() && _regex.partial_match(std::string_view(src));
    }
    [[nodiscard]] const vespalib::Regex& get_regex() const noexcept { return _regex; }
};

} // namespace search::attribute
