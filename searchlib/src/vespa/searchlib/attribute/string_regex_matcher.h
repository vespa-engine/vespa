// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_matcher_base.h"
#include "string_search_helper.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

#include <string>

namespace search {
template <class EntryT> class EnumStoreT;
} // namespace search

namespace search::attribute {

class EnumHintSearchContext;

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string value, using regex matching.
 */
class StringRegexMatcher : public StringMatcherBase {
private:
    StringSearchHelper _helper;

public:
    StringRegexMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased);
    StringRegexMatcher(StringRegexMatcher&&) noexcept;
    ~StringRegexMatcher();

protected:
    [[nodiscard]] bool match(const char* src) const { return _helper.isMatch(src); }
    [[nodiscard]] std::string get_prefix() const;
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
        if (match(enum_store.get_value(it.getKey().load_acquire()))) {
            return true;
        }
        ++it;
        return false;
    }
};

} // namespace search::attribute
