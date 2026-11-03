// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "string_search_helper.h"

#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>
#include <vespa/vespalib/util/regexp.h>

namespace search {
class QueryTermSimple;
} // namespace search

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

    bool isValid() const;
    bool match(const char* src) const { return _helper.isMatch(src); }
    bool isPrefix() const { return _helper.isPrefix(); }
    bool isRegex() const { return _helper.isRegex(); }
    bool isCased() const { return _helper.isCased(); }
    bool isFuzzy() const { return _helper.isFuzzy(); }
    const vespalib::Regex& getRegex() const { return _helper.getRegex(); }
    const vespalib::FuzzyMatcher& getFuzzyMatcher() const { return _helper.getFuzzyMatcher(); }
    const QueryTermUCS4* get_query_term_ptr() const noexcept { return _query_term.get(); }

    template <typename DictionaryConstIteratorType>
    bool is_fuzzy_match(const char* word, DictionaryConstIteratorType& itr,
                        const DfaStringComparator::DataStoreType& data_store) const {
        return _helper.is_fuzzy_match(word, itr, data_store);
    }
    void setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc);
    bool lookup_in_dictionary(const EnumStoreT<const char*>& enum_store, IDictionaryLookup& dict) const;

    /*
     * The methods below are needed in StringPostingSearchContext
     */
    template <typename PostingSearchContextAdapterType>
    bool setup_posting_sc(const EnumStoreT<const char*>&   enum_store,
                          PostingSearchContextAdapterType& posting_sc) const {
        if (isValid()) {
            if (isPrefix()) {
                auto comp = enum_store.prefix_lookup_comparator(get_query_term_ptr()->getTerm());
                posting_sc.lookupRange(comp, comp);
            } else if (isRegex()) {
                std::string prefix(vespalib::RegexpUtil::get_prefix(get_query_term_ptr()->getTerm()));
                auto        comp = enum_store.prefix_lookup_comparator(prefix.c_str());
                posting_sc.lookupRange(comp, comp);
            } else if (isFuzzy()) {
                std::string prefix(getFuzzyMatcher().getPrefix());
                auto        comp = enum_store.prefix_lookup_comparator(prefix.c_str());
                posting_sc.lookupRange(comp, comp);
            } else {
                auto comp = enum_store.string_lookup_comparator(get_query_term_ptr()->getTerm());
                posting_sc.lookupTerm(comp);
            }
            return true;
        }

        return false;
    }

    template <typename DictionaryConstIteratorType>
    bool use_dictionary_entry(const EnumStoreT<const char*>& enum_store, DictionaryConstIteratorType& it) const {
        if (isRegex()) {
            if (getRegex().valid() && getRegex().partial_match(enum_store.get_value(it.getKey().load_acquire()))) {
                return true;
            }
            ++it;
            return false;
        } else if (isCased()) {
            if (match(enum_store.get_value(it.getKey().load_acquire()))) {
                return true;
            }
            ++it;
            return false;
        } else if (isFuzzy()) {
            return is_fuzzy_match(enum_store.get_value(it.getKey().load_acquire()), it, enum_store.get_data_store());
        }
        return true;
    }

    bool use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& info, uint32_t docid_limit) const;
};

} // namespace search::attribute
