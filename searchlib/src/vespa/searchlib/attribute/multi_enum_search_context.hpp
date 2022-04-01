// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_enum_search_context.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

template <typename T, typename Matcher, typename M>
MultiEnumSearchContext<T, Matcher, M>::MultiEnumSearchContext(Matcher&& matcher, const AttributeVector& toBeSearched, const MultiValueMapping<M>& mv_mapping, const EnumStoreT<T>& enum_store)
    : Matcher(std::move(matcher)),
      SearchContext(toBeSearched),
      _mv_mapping(mv_mapping),
      _enum_store(enum_store)
{
}

template <typename T, typename Matcher, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiEnumSearchContext<T, Matcher, M>::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
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
