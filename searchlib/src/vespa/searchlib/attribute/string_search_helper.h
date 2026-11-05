// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <memory>

namespace search {
class QueryTermUCS4;
}

namespace search::attribute {

/**
 * Helper class for search context when scanning string fields for exact or prefix matches.
 */
class StringSearchHelper {
public:
    StringSearchHelper(QueryTermUCS4& qTerm, bool cased);
    StringSearchHelper(StringSearchHelper&&) noexcept;
    StringSearchHelper(const StringSearchHelper&) = delete;
    StringSearchHelper& operator=(const StringSearchHelper&) = delete;
    ~StringSearchHelper();
    bool is_match(const char* src) const noexcept;
    bool is_prefix() const noexcept { return _is_prefix; }
    bool is_cased() const noexcept { return _is_cased; }

private:
    using ucs4_t = uint32_t;
    std::unique_ptr<ucs4_t[]> _ucs4;
    const char*               _term;
    uint32_t                  _term_len; // measured in bytes
    bool                      _is_prefix;
    bool                      _is_cased;
};

} // namespace search::attribute
