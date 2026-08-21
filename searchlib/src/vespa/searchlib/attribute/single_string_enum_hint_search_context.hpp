// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "single_string_enum_hint_search_context.h"

#include <vespa/searchlib/query/query_term_ucs4.h>

namespace search::attribute {

template <typename Matcher>
SingleStringEnumHintSearchContextT<Matcher>::SingleStringEnumHintSearchContextT(
    Matcher&& matcher, const AttributeVector& toBeSearched,
    typename SingleStringEnumHintSearchContextT::EnumIndices enum_indices, const EnumStoreT<const char*>& enum_store,
    uint64_t num_values)
    : SingleStringEnumSearchContextT<Matcher>(std::move(matcher), toBeSearched, enum_indices, enum_store),
      EnumHintSearchContext(enum_store.get_dictionary(), enum_indices.size(), num_values) {
    this->set_and_setup_enum_hint_sc(enum_store, *this);
}

template <typename Matcher>
SingleStringEnumHintSearchContextT<Matcher>::~SingleStringEnumHintSearchContextT() = default;

} // namespace search::attribute
