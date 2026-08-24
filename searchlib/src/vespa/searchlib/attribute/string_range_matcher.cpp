// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_range_matcher.h"

#include "enumhintsearchcontext.h"
#include "enumstore.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search::attribute {

StringRangeMatcher::StringRangeMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased)
    : _query_term(std::move(query_term)),
      _helper(_query_term ? _query_term->get_string_range_spec() : nullptr, cased) {
}

StringRangeMatcher::StringRangeMatcher(StringRangeMatcher&&) noexcept = default;

StringRangeMatcher::~StringRangeMatcher() = default;

void StringRangeMatcher::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                            EnumHintSearchContext&         enum_hint_sc) {
    if (_helper.is_valid()) {
        auto* range_spec = _helper.get_string_range_spec();
        // This is used to eliminate searches that do not yield any results.
        // We do not use the possible (half-)openness of the range here: If we do not get any result here,
        // we do not have any results for the closed range and, hence, also not any hits if the range is (half-)open.
        // TODO Use unboundedness
        auto comp_left = enum_store.make_folded_comparator(range_spec->left.c_str());
        auto comp_right = enum_store.make_folded_comparator(range_spec->right.c_str());
        enum_hint_sc.lookupRange(comp_left, comp_right);
    }
}

} // namespace search::attribute
