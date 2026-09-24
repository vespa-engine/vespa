// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <memory>

namespace search {
class QueryTermUCS4;
}

namespace search::attribute {

/**
 * Helper class for StringUncasedMatcher that implements the actual matching logic
 * for uncased word and prefix matching.
 * Separate class from StringUncasedMatcher for unit testing.
 */
class StringUncasedSearchHelper {
private:
    using ucs4_t = uint32_t;
    std::unique_ptr<ucs4_t[]> _ucs4;
    bool                      _is_prefix;

public:
    explicit StringUncasedSearchHelper(const QueryTermUCS4& term);
    StringUncasedSearchHelper(StringUncasedSearchHelper&&) noexcept;
    StringUncasedSearchHelper(const StringUncasedSearchHelper&) = delete;
    StringUncasedSearchHelper& operator=(const StringUncasedSearchHelper&) = delete;
    ~StringUncasedSearchHelper();

    [[nodiscard]] bool is_match(const char* src) const noexcept;
    [[nodiscard]] bool is_prefix() const noexcept { return _is_prefix; }
};

} // namespace search::attribute
