// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <span>

namespace vespalib {

/**
 * Fuzzy matching between lowercased instances of query and document terms based on Levenshtein distance.
 * Class has two main parameters:
 * - prefix size, i.e the size of the prefix that is considered frozen.
 * - max edit distance, i.e an upper bound for edit distance for it to be a match between terms
 * Prefix size dictates of how match of a prefix is frozen,
 * i.e. if prefixes between the document and the query do not match (after lowercase)
 * matcher would return false early, without fuzzy match.
 */
class FuzzyMatcher {
private:
    static constexpr uint32_t DefaultPrefixSize = 0u;
    static constexpr uint32_t DefaultMaxEditDistance = 2u;

    uint32_t _max_edit_distance; // max edit distance
    uint32_t _prefix_size;       // prefix of a term that is considered frozen, i.e. non-fuzzy
    bool     _is_cased;

    std::vector<uint32_t> _folded_term_codepoints;

    std::span<const uint32_t> _folded_term_codepoints_prefix;
    std::span<const uint32_t> _folded_term_codepoints_suffix;

public:
    FuzzyMatcher();
    FuzzyMatcher(const FuzzyMatcher &) = delete;
    FuzzyMatcher & operator = (const FuzzyMatcher &) = delete;
    FuzzyMatcher(std::string_view term, uint32_t max_edit_distance, uint32_t prefix_size, bool is_cased);
    ~FuzzyMatcher();

    [[nodiscard]] bool isMatch(std::string_view target) const;
    [[nodiscard]] vespalib::string getPrefix() const;

    static std::span<const uint32_t> get_prefix(const std::vector<uint32_t>& termCodepoints, uint32_t prefixLength);
    static std::span<const uint32_t> get_suffix(const std::vector<uint32_t>& termCodepoints, uint32_t prefixLength);
};

}
