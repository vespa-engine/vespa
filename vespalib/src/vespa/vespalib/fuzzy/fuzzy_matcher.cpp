// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fuzzy_matcher.h"
#include "levenshtein_distance.h"

#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>

vespalib::FuzzyMatcher vespalib::FuzzyMatcher::from_term(std::string_view term) {
    return FuzzyMatcher(LowerCase::convert_to_ucs4(term));
}

bool vespalib::FuzzyMatcher::isMatch(std::string_view target) const {
    std::vector<uint32_t> targetCodepoints = LowerCase::convert_to_ucs4(target);

    return LevenshteinDistance::calculate(
            _folded_term_codepoints,
            targetCodepoints,
            _max_edit_distance).has_value();
}

vespalib::string vespalib::FuzzyMatcher::getPrefix() const {
    vespalib::string prefix;
    Utf8Writer writer(prefix);
    for (uint8_t i=0; i <_prefix_size && i <_folded_term_codepoints.size(); ++i) {
        writer.putChar(_folded_term_codepoints.at(i));
    }
    return prefix;
}
