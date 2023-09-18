// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dfa_fuzzy_matcher.h"

using vespalib::fuzzy::LevenshteinDfa;

namespace search::attribute {

DfaFuzzyMatcher::DfaFuzzyMatcher(std::string_view target, uint8_t max_edits, bool cased, LevenshteinDfa::DfaType dfa_type)
    : _dfa(vespalib::fuzzy::LevenshteinDfa::build(target, max_edits, (cased ? LevenshteinDfa::Casing::Cased : LevenshteinDfa::Casing::Uncased), dfa_type)),
      _successor()
{
}

DfaFuzzyMatcher::~DfaFuzzyMatcher() = default;

}
