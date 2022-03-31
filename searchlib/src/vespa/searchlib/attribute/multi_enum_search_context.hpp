// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_enum_search_context.h"

namespace search::attribute {

template <typename T, typename Matcher, typename M>
MultiEnumSearchContext<T, Matcher, M>::MultiEnumSearchContext(Matcher&& matcher, const AttributeVector& toBeSearched, const MultiValueMapping<M>& mv_mapping, const EnumStoreT<T>& enum_store)
    : Matcher(std::move(matcher)),
      SearchContext(toBeSearched),
      _mv_mapping(mv_mapping),
      _enum_store(enum_store)
{
}

}
