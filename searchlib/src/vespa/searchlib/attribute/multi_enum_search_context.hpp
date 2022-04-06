// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_enum_search_context.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

template <typename T, typename BaseSC, typename M>
MultiEnumSearchContext<T, BaseSC, M>::MultiEnumSearchContext(typename BaseSC::MatcherType&& matcher, const AttributeVector& toBeSearched, MultiValueMappingReadView<M> mv_mapping_read_view, const EnumStoreT<T>& enum_store)
    : BaseSC(toBeSearched, std::move(matcher)),
      _mv_mapping_read_view(mv_mapping_read_view),
      _enum_store(enum_store)
{
}

template <typename T, typename BaseSC, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiEnumSearchContext<T, BaseSC, M>::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!this->valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (this->getIsFilter()) {
        return strict
            ? std::make_unique<FilterAttributeIteratorStrict<MultiEnumSearchContext>>(*this, matchData)
            : std::make_unique<FilterAttributeIteratorT<MultiEnumSearchContext>>(*this, matchData);
    }
    return strict
        ? std::make_unique<AttributeIteratorStrict<MultiEnumSearchContext>>(*this, matchData)
        : std::make_unique<AttributeIteratorT<MultiEnumSearchContext>>(*this, matchData);
}

}
