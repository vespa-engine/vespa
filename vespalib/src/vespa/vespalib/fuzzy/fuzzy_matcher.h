// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string_view>
#include <vector>

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/**
 * Fuzzy matching between lowercased instances of query and document terms based on Levenshtein distance.
 * Class has two main parameters:
 * - prefix size, i.e the size of the prefix that is considered frozen.
 * - max edit distance, i.e an upper bound for edit distance for it to be a match between terms
 * Note that prefix size is not impacting matching logic,
 * it's expected to be used to generate prefix for the lookup in the BTree for the fast-search logic.
 * But there is no code to actually enforcing it.
 */
class FuzzyMatcher {
private:
    static constexpr uint8_t DefaultPrefixSize = 0u;
    static constexpr uint8_t DefaultMaxEditDistance = 2u;

    std::vector<uint32_t> _folded_term_codepoints;

    uint8_t _max_edit_distance; // max edit distance
    uint8_t _prefix_size;  // prefix of a term that is considered frozen, i.e. non-fuzzy

public:
    FuzzyMatcher():
            _folded_term_codepoints(),
            _max_edit_distance(DefaultMaxEditDistance),
            _prefix_size(DefaultPrefixSize)
    {}

    FuzzyMatcher(std::vector<uint32_t> codepoints):
            _folded_term_codepoints(std::move(codepoints)),
            _max_edit_distance(DefaultMaxEditDistance),
            _prefix_size(DefaultPrefixSize)
    {}

    FuzzyMatcher(std::vector<uint32_t> codepoints, uint8_t max_edit_distance, uint8_t prefix_size):
            _folded_term_codepoints(std::move(codepoints)),
            _max_edit_distance(max_edit_distance),
            _prefix_size(prefix_size)
    {}

    [[nodiscard]] bool isMatch(std::string_view target) const;

    [[nodiscard]] vespalib::string getPrefix() const;

    ///

    static FuzzyMatcher from_term(std::string_view term);

};

}
