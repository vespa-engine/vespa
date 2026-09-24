// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_fuzzy_search_helper.h"

#include "dfa_fuzzy_matcher.h"
#include "i_enum_store_dictionary.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>

namespace search::attribute {

using FMA = vespalib::FuzzyMatchingAlgorithm;
using LDT = vespalib::fuzzy::LevenshteinDfa::DfaType;

namespace {

LDT to_dfa_type(FMA algorithm) {
    switch (algorithm) {
    case FMA::DfaImplicit:
        return LDT::Implicit;
    case FMA::DfaExplicit:
        return LDT::Explicit;
    case FMA::DfaTable:
        return LDT::Table;
    default:
        return LDT::Implicit;
    }
}

} // namespace

StringFuzzySearchHelper::StringFuzzySearchHelper(const QueryTermUCS4& term, bool cased,
                                                 vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm)
    : _fuzzy_matcher(), _dfa_fuzzy_matcher() {
    const auto max_edit_dist = term.fuzzy_max_edit_distance();
    _fuzzy_matcher = std::make_unique<vespalib::FuzzyMatcher>(
        term.getTerm(), max_edit_dist, term.fuzzy_prefix_lock_length(), cased, term.fuzzy_prefix_match());
    if ((fuzzy_matching_algorithm != FMA::BruteForce) && (max_edit_dist > 0 && max_edit_dist <= 2)) {
        _dfa_fuzzy_matcher =
            std::make_unique<DfaFuzzyMatcher>(term.getTerm(), max_edit_dist, term.fuzzy_prefix_lock_length(), cased,
                                              term.fuzzy_prefix_match(), to_dfa_type(fuzzy_matching_algorithm));
    }
}

StringFuzzySearchHelper::StringFuzzySearchHelper(StringFuzzySearchHelper&&) noexcept = default;

StringFuzzySearchHelper::~StringFuzzySearchHelper() = default;

bool StringFuzzySearchHelper::is_match(const char* src) const noexcept {
    return _dfa_fuzzy_matcher ? _dfa_fuzzy_matcher->is_match(std::string_view(src))
                              : _fuzzy_matcher->isMatch(std::string_view(src));
}

std::string StringFuzzySearchHelper::get_prefix() const {
    return _fuzzy_matcher->getPrefix();
}

template <typename DictionaryConstIteratorType>
bool StringFuzzySearchHelper::is_match(const char* word, DictionaryConstIteratorType& itr,
                                       const DfaStringComparator::DataStoreType& data_store) const {
    if (_dfa_fuzzy_matcher) {
        return _dfa_fuzzy_matcher->is_match(word, itr, data_store);
    } else {
        if (_fuzzy_matcher->isMatch(word)) {
            return true;
        }
        ++itr;
        return false;
    }
}

template bool StringFuzzySearchHelper::is_match(const char*, EnumPostingTree::ConstIterator&,
                                                const DfaStringComparator::DataStoreType&) const;

template bool StringFuzzySearchHelper::is_match(const char*, EnumTree::ConstIterator&,
                                                const DfaStringComparator::DataStoreType&) const;

} // namespace search::attribute
