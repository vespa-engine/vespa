// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_fuzzy_search_helper.h"
#include "string_matcher_base.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

#include <string>

namespace search {
template <class EntryT> class EnumStoreT;
} // namespace search

namespace search::attribute {

class EnumHintSearchContext;

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string value, using fuzzy matching.
 */
class StringFuzzyMatcher : public StringMatcherBase {
private:
    StringFuzzySearchHelper _helper;

public:
    StringFuzzyMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                       vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm);
    StringFuzzyMatcher(StringFuzzyMatcher&&) noexcept;
    ~StringFuzzyMatcher();

protected:
    [[nodiscard]] bool match(const char* src) const { return _helper.is_match(src); }
    [[nodiscard]] std::string get_prefix() const { return _helper.get_prefix(); }
    void setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc);

    /*
     * Looks up the range of dictionary entries that are candidates for a match.
     */
    template <typename EnumStoreType, typename LookupContext>
    void lookup_dictionary(const EnumStoreType& enum_store, LookupContext& ctx) const {
        std::string prefix(get_prefix());
        auto        comp = enum_store.prefix_lookup_comparator(prefix.c_str());
        ctx.lookupRange(comp, comp);
    }

    /*
     * Checks if a dictionary entry in the looked up range is a match.
     * Steps the dictionary iterator one or more steps when not matching.
     */
    template <typename EnumStoreType, typename DictionaryConstIterator>
    [[nodiscard]] bool match_dictionary_entry(const EnumStoreType& enum_store, DictionaryConstIterator& it) const {
        return _helper.is_match(enum_store.get_value(it.getKey().load_acquire()), it, enum_store.get_data_store());
    }
};

} // namespace search::attribute
