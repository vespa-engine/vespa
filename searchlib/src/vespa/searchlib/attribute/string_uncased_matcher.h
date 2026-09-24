// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_matcher_base.h"
#include "string_uncased_search_helper.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search {
template <class EntryT> class EnumStoreT;
} // namespace search

namespace search::attribute {

class EnumHintSearchContext;

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string value, using uncased word or prefix matching.
 */
class StringUncasedMatcher : public StringMatcherBase {
private:
    StringUncasedSearchHelper _helper;

public:
    explicit StringUncasedMatcher(std::unique_ptr<QueryTermSimple> query_term);
    StringUncasedMatcher(StringUncasedMatcher&&) noexcept;
    ~StringUncasedMatcher();

protected:
    [[nodiscard]] bool match(const char* src) const { return _helper.is_match(src); }
    void setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc);

    /*
     * Looks up the range of dictionary entries that are candidates for a match.
     */
    template <typename EnumStoreType, typename LookupContext>
    void lookup_dictionary(const EnumStoreType& enum_store, LookupContext& ctx) const {
        if (_helper.is_prefix()) {
            auto comp = enum_store.prefix_lookup_comparator(get_query_term().getTerm());
            ctx.lookupRange(comp, comp);
        } else {
            auto comp = enum_store.string_lookup_comparator(get_query_term().getTerm());
            ctx.lookupTerm(comp);
        }
    }

    /*
     * Checks if a dictionary entry in the looked up range is a match.
     * Steps the dictionary iterator one or more steps when not matching.
     */
    template <typename EnumStoreType, typename DictionaryConstIterator>
    [[nodiscard]] bool match_dictionary_entry(const EnumStoreType& enum_store, DictionaryConstIterator& it) const {
        // The dictionary is folded, all entries in the looked up range are matches.
        (void)enum_store;
        (void)it;
        return true;
    }
};

} // namespace search::attribute
