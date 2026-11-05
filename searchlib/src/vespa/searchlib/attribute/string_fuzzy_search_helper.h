// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dfa_string_comparator.h"

#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace vespalib {
class FuzzyMatcher;
}
namespace search {
class QueryTermUCS4;
}

namespace search::attribute {

class DfaFuzzyMatcher;

/**
 * Helper class for search context when scanning string fields via fuzzy search.
 */
class StringFuzzySearchHelper {
public:
    using FuzzyMatcher = vespalib::FuzzyMatcher;
    StringFuzzySearchHelper(QueryTermUCS4& term, bool cased,
                            vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm);
    StringFuzzySearchHelper(StringFuzzySearchHelper&&) noexcept;
    StringFuzzySearchHelper(const StringFuzzySearchHelper&) = delete;
    StringFuzzySearchHelper& operator=(const StringFuzzySearchHelper&) = delete;
    ~StringFuzzySearchHelper();
    bool is_match(const char* src) const noexcept;
    bool is_cased() const noexcept { return _is_cased; }
    const FuzzyMatcher& get_fuzzy_matcher() const noexcept { return *_fuzzy_matcher; }

    template <typename DictionaryConstIteratorType>
    bool is_fuzzy_match(const char* word, DictionaryConstIteratorType& itr,
                        const DfaStringComparator::DataStoreType& data_store) const;

private:
    std::unique_ptr<FuzzyMatcher>    _fuzzy_matcher;
    std::unique_ptr<DfaFuzzyMatcher> _dfa_fuzzy_matcher;
    bool                             _is_cased;
};

} // namespace search::attribute
