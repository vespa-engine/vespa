// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_search_helper.h"
#include "dfa_fuzzy_matcher.h"
#include "i_enum_store_dictionary.h"
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>


namespace search::attribute {

using FMA = vespalib::FuzzyMatchingAlgorithm;
using LDT = vespalib::fuzzy::LevenshteinDfa::DfaType;

namespace {

LDT
to_dfa_type(FMA algorithm)
{
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

}

StringSearchHelper::StringSearchHelper(QueryTermUCS4 & term, bool cased, vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm)
    : _regex(),
      _fuzzyMatcher(),
      _dfa_fuzzy_matcher(),
      _term(),
      _termLen(),
      _isPrefix(term.isPrefix()),
      _isRegex(term.isRegex()),
      _isCased(cased),
      _isFuzzy(term.isFuzzy())
{
    if (isRegex()) {
        _regex = (isCased())
                ? vespalib::Regex::from_pattern(term.getTerm(), vespalib::Regex::Options::None)
                : vespalib::Regex::from_pattern(term.getTerm(), vespalib::Regex::Options::IgnoreCase);
    } else if (isFuzzy()) {
        auto max_edit_dist = term.getFuzzyMaxEditDistance();
        _fuzzyMatcher = std::make_unique<vespalib::FuzzyMatcher>(term.getTerm(),
                                                                 max_edit_dist,
                                                                 term.getFuzzyPrefixLength(),
                                                                 isCased());
        if ((fuzzy_matching_algorithm != FMA::BruteForce) &&
            (max_edit_dist > 0 && max_edit_dist <= 2)) {
            _dfa_fuzzy_matcher = std::make_unique<DfaFuzzyMatcher>(term.getTerm(),
                                                                   max_edit_dist,
                                                                   term.getFuzzyPrefixLength(),
                                                                   isCased(),
                                                                   to_dfa_type(fuzzy_matching_algorithm));
        }
    } else if (isCased()) {
        _term = term.getTerm();
        _termLen = strlen(_term);
    } else {
        _ucs4 = term.asUcs4();
    }
}

StringSearchHelper::StringSearchHelper(StringSearchHelper&&) noexcept = default;

StringSearchHelper::~StringSearchHelper() = default;

bool
StringSearchHelper::isMatch(const char *src) const noexcept {
    if (__builtin_expect(isRegex(), false)) {
        return getRegex().valid() && getRegex().partial_match(std::string_view(src));
    }
    if (__builtin_expect(isFuzzy(), false)) {
        return _dfa_fuzzy_matcher ? _dfa_fuzzy_matcher->is_match(src) : getFuzzyMatcher().isMatch(src);
    }
    if (__builtin_expect(isCased(), false)) {
        int res = strncmp(_term, src, _termLen);
        return (res == 0) && (src[_termLen] == 0 || isPrefix());
    }
    vespalib::Utf8ReaderForZTS u8reader(src);
    uint32_t j = 0;
    uint32_t val;
    for (;; ++j) {
        val = u8reader.getChar();
        val = vespalib::LowerCase::convert(val);
        if (_ucs4[j] == 0 || _ucs4[j] != val) {
            break;
        }
    }
    return (_ucs4[j] == 0 && (val == 0 || isPrefix()));
}

template <typename DictionaryConstIteratorType>
bool
StringSearchHelper::is_fuzzy_match(const char* word, DictionaryConstIteratorType& itr, const DfaStringComparator::DataStoreType& data_store) const
{
    if (_dfa_fuzzy_matcher) {
        return _dfa_fuzzy_matcher->is_match(word, itr, data_store);
    } else {
        if (_fuzzyMatcher->isMatch(word)) {
            return true;
        }
        ++itr;
        return false;
    }
}

template
bool
StringSearchHelper::is_fuzzy_match(const char*, EnumPostingTree::ConstIterator&, const DfaStringComparator::DataStoreType&) const;

template
bool
StringSearchHelper::is_fuzzy_match(const char*, EnumTree::ConstIterator&, const DfaStringComparator::DataStoreType&) const;

}
