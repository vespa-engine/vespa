// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_range_search_helper.h"

#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search {
template <class EntryT> class EnumStoreT;
} // namespace search

namespace search::queryeval {
class ExecuteInfo;
}

namespace search::attribute {

class EnumHintSearchContext;
class IDictionaryLookup;

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string range.
 */
class StringRangeMatcher {
private:
    std::unique_ptr<QueryTermSimple> _query_term;
    StringRangeSearchHelper          _helper;

public:
    StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased);
    StringRangeMatcher(StringRangeMatcher&&) noexcept;
    ~StringRangeMatcher();

    [[nodiscard]] const StringRangeSpec* get_string_range_spec() const { return _helper.get_string_range_spec(); }

    [[nodiscard]] bool isValid() const { return _helper.is_valid(); }
    [[nodiscard]] bool match(const char* src) const { return _helper.is_match(src); }
    [[nodiscard]] const QueryTermUCS4* get_query_term_ptr() const noexcept {
        return dynamic_cast<QueryTermUCS4*>(_query_term.get());
    }

    bool lookup_in_dictionary(const EnumStoreT<const char*>& enum_store, IDictionaryLookup& dict) const;

    /*
     * The methods below are needed in StringPostingSearchContext
     */
    template <typename DictionaryConstIteratorType>
    bool use_dictionary_entry(const EnumStoreT<const char*>& /*enum_store*/,
                              DictionaryConstIteratorType& /*it*/) const {
        return true;
    }

    bool use_posting_lists_when_non_strict(const queryeval::ExecuteInfo& /*info*/, uint32_t /*docid_limit*/) const {
        return false;
    }
};

} // namespace search::attribute
