// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "string_search_helper.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>
#include <vespa/vespalib/util/regexp.h>

namespace search {
class QueryTermSimple;
} // namespace search

namespace search::queryeval {
class ExecuteInfo;
}

namespace search::attribute {

class EnumHintSearchContext;
class IDictionaryLookup;

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string value.
 */
class StringMatcher {
private:
    std::unique_ptr<QueryTermUCS4> _query_term;
    attribute::StringSearchHelper  _helper;

public:
    StringMatcher(std::unique_ptr<QueryTermSimple> qTerm, bool cased,
                  vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm);
    StringMatcher(StringMatcher&&) noexcept;
    ~StringMatcher();

    [[nodiscard]] bool is_valid() const { return _query_term && (!_query_term->empty()); }
    [[nodiscard]] bool match(const char* src) const { return _helper.isMatch(src); }
    [[nodiscard]] const QueryTermUCS4* get_query_term_ptr() const noexcept { return _query_term.get(); }

    bool lookup_in_dictionary(const EnumStoreT<const char*>& enum_store, IDictionaryLookup& dict) const;

    /*
     * The methods below are needed in StringPostingSearchContext
     */
    template <typename DictionaryConstIteratorType>
    bool use_dictionary_entry(const EnumStoreT<const char*>& enum_store, DictionaryConstIteratorType& it) const {
        if (_helper.isRegex()) {
            if (_helper.getRegex().valid() &&
                _helper.getRegex().partial_match(enum_store.get_value(it.getKey().load_acquire())))
            {
                return true;
            }
            ++it;
            return false;
        } else if (_helper.isCased()) {
            if (match(enum_store.get_value(it.getKey().load_acquire()))) {
                return true;
            }
            ++it;
            return false;
        } else if (_helper.isFuzzy()) {
            return _helper.is_fuzzy_match(enum_store.get_value(it.getKey().load_acquire()), it,
                                          enum_store.get_data_store());
        }
        return true;
    }

    bool use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& info, uint32_t docid_limit) const;
};

} // namespace search::attribute
