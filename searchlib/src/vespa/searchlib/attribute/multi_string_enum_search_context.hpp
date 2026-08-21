// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_enum_search_context.h"
#include "multi_string_enum_search_context.h"

#include <vespa/searchlib/query/query_term_simple.h>

namespace search::attribute {

template <typename M, typename Matcher>
MultiStringEnumSearchContextT<M, Matcher>::MultiStringEnumSearchContextT(
    Matcher&& matcher, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view,
    const EnumStoreT<const char*>& enum_store)
    : MultiEnumSearchContext<const char*, StringSearchContextT<Matcher>, M>(std::move(matcher), toBeSearched,
                                                                            mv_mapping_read_view, enum_store) {
}

template <typename M, typename Matcher>
MultiStringEnumSearchContextT<M, Matcher>::~MultiStringEnumSearchContextT() = default;

} // namespace search::attribute
