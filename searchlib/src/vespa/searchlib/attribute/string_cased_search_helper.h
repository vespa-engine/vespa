// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search {
class QueryTermUCS4;
}

namespace search::attribute {

/**
 * Helper class for StringCasedMatcher that implements the actual matching logic
 * for cased word and prefix matching.
 * Separate class from StringCasedMatcher for unit testing.
 */
class StringCasedSearchHelper {
private:
    const char* _term;
    uint32_t    _term_len; // measured in bytes
    bool        _is_prefix;

public:
    explicit StringCasedSearchHelper(const QueryTermUCS4& term);
    StringCasedSearchHelper(StringCasedSearchHelper&&) noexcept;
    StringCasedSearchHelper(const StringCasedSearchHelper&) = delete;
    StringCasedSearchHelper& operator=(const StringCasedSearchHelper&) = delete;
    ~StringCasedSearchHelper();

    [[nodiscard]] bool is_match(const char* src) const noexcept;
    [[nodiscard]] bool is_prefix() const noexcept { return _is_prefix; }
};

} // namespace search::attribute
