// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fuzzy_matcher.h"
#include "levenshtein_distance.h"

#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>

vespalib::FuzzyMatcher vespalib::FuzzyMatcher::from_term(std::string_view term, uint32_t maxEditDistance, uint32_t prefixLength) {
    return FuzzyMatcher(LowerCase::convert_to_ucs4(term), maxEditDistance, prefixLength);
}

std::span<const uint32_t> vespalib::FuzzyMatcher::get_prefix(const std::vector<uint32_t>& termCodepoints, uint32_t prefixLength) {
    if (prefixLength == 0 || termCodepoints.empty()) {
        return {};
    } else {
        uint32_t actualPrefixLength = std::min(prefixLength, static_cast<uint32_t>(termCodepoints.size()));
        return {termCodepoints.begin(), termCodepoints.begin() + actualPrefixLength};
    }
}

std::span<const uint32_t> vespalib::FuzzyMatcher::get_suffix(const std::vector<uint32_t>& termCodepoints, uint32_t prefixLength) {
    if (termCodepoints.empty()) {
        return {};
    } else {
        uint32_t actualPrefixLength = std::min(prefixLength, static_cast<uint32_t>(termCodepoints.size()));
        return {termCodepoints.begin() + actualPrefixLength, termCodepoints.end()};
    }
}

bool vespalib::FuzzyMatcher::isMatch(std::string_view target) const {
    std::vector<uint32_t> targetCodepoints = LowerCase::convert_to_ucs4(target);

    if (_prefix_size > 0) { // prefix comparison is meaningless if it's empty
        std::span<const uint32_t> targetPrefix = get_prefix(targetCodepoints, _prefix_size);
        // if prefix does not match, early stop
        if (!std::equal(_folded_term_codepoints_prefix.begin(), _folded_term_codepoints_prefix.end(),
                        targetPrefix.begin(), targetPrefix.end())) {
            return false;
        }
    }

    return LevenshteinDistance::calculate(
            _folded_term_codepoints_suffix,
            get_suffix(targetCodepoints, _prefix_size),
            _max_edit_distance).has_value();
}

vespalib::string vespalib::FuzzyMatcher::getPrefix() const {
    vespalib::string prefix;
    Utf8Writer writer(prefix);
    for (const uint32_t& code: _folded_term_codepoints_prefix) {
        writer.putChar(code);
    }
    return prefix;
}
