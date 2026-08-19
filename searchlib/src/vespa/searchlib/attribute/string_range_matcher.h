// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string_search_helper.h"

#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/vespalib/fuzzy/fuzzy_matching_algorithm.h>
#include <vespa/vespalib/util/hdr_abort.h>

namespace search {
class QueryTermSimple;
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
    const StringRangeSpec*           _range_spec;
    bool                             _cased;

public:
    StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                       vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm);
    StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased);
    StringRangeMatcher(StringRangeMatcher&&) noexcept;
    ~StringRangeMatcher();

    const StringRangeSpec* get_string_range_spec() const { return _range_spec; }

protected:
    bool isValid() const;
    bool match(const char* src) const;
    const QueryTermUCS4* get_query_term_ptr() const noexcept { HDR_ABORT("should not be reached"); }

    void setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store, EnumHintSearchContext& enum_hint_sc);

private:
    template <bool fold>
    bool match_internal(const char* src) const;
};

} // namespace search::attribute
