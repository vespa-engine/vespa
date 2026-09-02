// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "single_enum_search_context.h"
#include "single_string_enum_search_context.h"

#include <vespa/searchlib/query/query_term_simple.h>

namespace search::attribute {

template <typename Matcher>
SingleStringEnumSearchContextT<Matcher>::SingleStringEnumSearchContextT(
    Matcher&& matcher, const AttributeVector& toBeSearched,
    typename SingleStringEnumSearchContextT::EnumIndices enum_indices, const EnumStoreT<const char*>& enum_store)
    : SingleEnumSearchContext<const char*, StringSearchContextT<Matcher>>(std::move(matcher), toBeSearched,
                                                                          enum_indices, enum_store) {
}

template <typename Matcher>
SingleStringEnumSearchContextT<Matcher>::SingleStringEnumSearchContextT(SingleStringEnumSearchContextT&&) noexcept =
    default;

template <typename Matcher>
SingleStringEnumSearchContextT<Matcher>::~SingleStringEnumSearchContextT() = default;

} // namespace search::attribute
