// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string_view>
#include <vector>
#include <span>

#include <vespa/vespalib/stllike/string.h>

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

    std::vector<uint32_t> _folded_term_codepoints;

    std::span<const uint32_t> _folded_term_codepoints_prefix;
    std::span<const uint32_t> _folded_term_codepoints_suffix;

public:
    FuzzyMatcher():
            _max_edit_distance(DefaultMaxEditDistance),
            _prefix_size(DefaultPrefixSize),
            _folded_term_codepoints(),
            _folded_term_codepoints_prefix(),
            _folded_term_codepoints_suffix()
    {}

    FuzzyMatcher(const std::vector<uint32_t>&& codepoints):
            _max_edit_distance(DefaultMaxEditDistance),
            _prefix_size(DefaultPrefixSize),
            _folded_term_codepoints(codepoints),
            _folded_term_codepoints_prefix(get_prefix(_folded_term_codepoints, _prefix_size)),
            _folded_term_codepoints_suffix(get_suffix(_folded_term_codepoints, _prefix_size))
    {}

    FuzzyMatcher(const std::vector<uint32_t>&& codepoints, uint32_t max_edit_distance, uint32_t prefix_size):
            _max_edit_distance(max_edit_distance),
            _prefix_size(prefix_size),
            _folded_term_codepoints(codepoints),
            _folded_term_codepoints_prefix(get_prefix(_folded_term_codepoints, _prefix_size)),
            _folded_term_codepoints_suffix(get_suffix(_folded_term_codepoints, _prefix_size))
    {}

    [[nodiscard]] bool isMatch(std::string_view target) const;

    [[nodiscard]] vespalib::string getPrefix() const;

    ///

    static FuzzyMatcher from_term(std::string_view term, uint32_t maxEditDistance, uint32_t prefixLength);

    static std::span<const uint32_t> get_prefix(const std::vector<uint32_t>& termCodepoints, uint32_t prefixLength);

    static std::span<const uint32_t> get_suffix(const std::vector<uint32_t>& termCodepoints, uint32_t prefixLength);

};

}
