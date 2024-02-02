// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fuzzy_term.h"

namespace search::streaming {

namespace {

constexpr bool normalizing_implies_cased(Normalizing norm) noexcept {
    return (norm == Normalizing::NONE);
}

}

FuzzyTerm::FuzzyTerm(std::unique_ptr<QueryNodeResultBase> result_base, stringref term,
                     const string& index, Type type, Normalizing normalizing,
                     uint8_t max_edits, uint32_t prefix_size)
    : QueryTerm(std::move(result_base), term, index, type, normalizing),
      _dfa_matcher(),
      _fallback_matcher()
{
    setFuzzyMaxEditDistance(max_edits);
    setFuzzyPrefixLength(prefix_size);

    std::string_view term_view(term.data(), term.size());
    const bool cased = normalizing_implies_cased(normalizing);
    if (attribute::DfaFuzzyMatcher::supports_max_edits(max_edits)) {
        _dfa_matcher = std::make_unique<attribute::DfaFuzzyMatcher>(term_view, max_edits, prefix_size, cased);
    } else {
        _fallback_matcher = std::make_unique<vespalib::FuzzyMatcher>(term_view, max_edits, prefix_size, cased);
    }
}

FuzzyTerm::~FuzzyTerm() = default;

bool FuzzyTerm::is_match(std::string_view term) const {
    if (_dfa_matcher) {
        return _dfa_matcher->is_match(term);
    } else {
        return _fallback_matcher->isMatch(term);
    }
}

}
