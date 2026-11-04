// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store_dictionary.h"
#include "string_search_helper.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search {
template <class EntryT> class EnumStoreT;
class QueryTermSimple;
} // namespace search

namespace search::queryeval {
class ExecuteInfo;
} // namespace search::queryeval

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

    /*
     * Methods for dictionary lookups. Used by StringPostingSearchContext.
     * lookup_in_dictionary is also used by StringSearchContext with an EnumHintSearchContext.
     */
    bool lookup_in_dictionary(const EnumStoreT<const char*>& enum_store, IDictionaryLookup& dict) const;
    bool use_dictionary_entry(const EnumStoreT<const char*>& enum_store, EnumPostingTree::ConstIterator& it) const;
    bool use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& info, uint32_t docid_limit) const;
};

} // namespace search::attribute
