// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "single_enum_search_context.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

template <typename T, typename BaseSC>
SingleEnumSearchContext<T, BaseSC>::SingleEnumSearchContext(typename BaseSC::MatcherType&& matcher, const AttributeVector& toBeSearched, EnumIndices enum_indices, const EnumStoreT<T>& enum_store)
    : BaseSC(toBeSearched, std::move(matcher)),
      _enum_indices(enum_indices),
      _enum_store(enum_store)
{
}

template <typename T, typename BaseSC>
std::unique_ptr<queryeval::SearchIterator>
SingleEnumSearchContext<T, BaseSC>::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!this->valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (this->getIsFilter()) {
        return strict
            ? std::make_unique<FilterAttributeIteratorStrict<SingleEnumSearchContext>>(*this, matchData)
            : std::make_unique<FilterAttributeIteratorT<SingleEnumSearchContext>>(*this, matchData);
    }
    return strict
        ? std::make_unique<AttributeIteratorStrict<SingleEnumSearchContext>>(*this, matchData)
        : std::make_unique<AttributeIteratorT<SingleEnumSearchContext>>(*this, matchData);
}

template <typename T, typename BaseSC>
uint32_t
SingleEnumSearchContext<T, BaseSC>::get_committed_docid_limit() const noexcept
{
    return _enum_indices.size();
}

}
