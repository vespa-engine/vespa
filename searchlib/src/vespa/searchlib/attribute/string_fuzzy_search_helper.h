// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dfa_string_comparator.h"

#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

#include <memory>
#include <string>

namespace vespalib {
class FuzzyMatcher;
}
namespace search {
class QueryTermUCS4;
}

namespace search::attribute {

class DfaFuzzyMatcher;

/**
 * Helper class for StringFuzzyMatcher that implements the actual matching logic
 * for fuzzy matching, using either a DFA or brute force.
 * Separate class from StringFuzzyMatcher for unit testing.
 */
class StringFuzzySearchHelper {
private:
    using FuzzyMatcher = vespalib::FuzzyMatcher;
    std::unique_ptr<FuzzyMatcher>    _fuzzy_matcher;
    std::unique_ptr<DfaFuzzyMatcher> _dfa_fuzzy_matcher;

public:
    StringFuzzySearchHelper(
        const QueryTermUCS4& term, bool cased,
        vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm = vespalib::FuzzyMatchingAlgorithm::BruteForce);
    StringFuzzySearchHelper(StringFuzzySearchHelper&&) noexcept;
    StringFuzzySearchHelper(const StringFuzzySearchHelper&) = delete;
    StringFuzzySearchHelper& operator=(const StringFuzzySearchHelper&) = delete;
    ~StringFuzzySearchHelper();

    [[nodiscard]] bool is_match(const char* src) const noexcept;
    [[nodiscard]] std::string get_prefix() const;

    /*
     * Checks if the dictionary word is a match. Steps the dictionary iterator one or more steps
     * when not matching.
     */
    template <typename DictionaryConstIteratorType>
    [[nodiscard]] bool is_match(const char* word, DictionaryConstIteratorType& itr,
                                const DfaStringComparator::DataStoreType& data_store) const;
};

} // namespace search::attribute
