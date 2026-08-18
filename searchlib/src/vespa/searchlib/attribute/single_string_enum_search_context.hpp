// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "single_enum_search_context.h"
#include "single_string_enum_search_context.h"

#include <vespa/searchlib/query/query_term_simple.h>

namespace search::attribute {

template <typename Matcher>
SingleStringEnumSearchContextT<Matcher>::SingleStringEnumSearchContextT(
    std::unique_ptr<QueryTermSimple> qTerm, bool cased, vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm,
    const AttributeVector& toBeSearched, typename SingleStringEnumSearchContextT::EnumIndices enum_indices,
    const EnumStoreT<const char*>& enum_store)
    : SingleEnumSearchContext<const char*, StringSearchContextT<Matcher>>(
          Matcher(std::move(qTerm), cased, fuzzy_matching_algorithm), toBeSearched, enum_indices, enum_store) {
}

template <typename Matcher>
SingleStringEnumSearchContextT<Matcher>::SingleStringEnumSearchContextT(SingleStringEnumSearchContextT&&) noexcept =
    default;

template <typename Matcher>
SingleStringEnumSearchContextT<Matcher>::~SingleStringEnumSearchContextT() = default;

} // namespace search::attribute
