// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_range_search_helper.h"

#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>

namespace search {
template <class EntryT> class EnumStoreT;
} // namespace search

namespace search::attribute {

class EnumHintSearchContext;

/*
 * Class used to determine if an attribute vector string value is a match for
 * the query string range.
 */
class StringRangeMatcher {
private:
    std::unique_ptr<QueryTermSimple> _query_term;
    StringRangeSearchHelper          _helper;

public:
    StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                       vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm);
    StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased);
    StringRangeMatcher(StringRangeMatcher&&) noexcept;
    ~StringRangeMatcher();

    [[nodiscard]] const StringRangeSpec* get_string_range_spec() const { return _helper.get_string_range_spec(); }

protected:
    [[nodiscard]] bool isValid() const { return _helper.is_valid(); }
    [[nodiscard]] bool match(const char* src) const { return _helper.is_match(src); }
    [[nodiscard]] const QueryTermUCS4* get_query_term_ptr() const noexcept {
        return dynamic_cast<QueryTermUCS4*>(_query_term.get());
    }

    void setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc);
};

} // namespace search::attribute
