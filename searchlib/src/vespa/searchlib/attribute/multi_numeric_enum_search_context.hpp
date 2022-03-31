// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_numeric_enum_search_context.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

template <typename T, typename M>
MultiNumericEnumSearchContext<T, M>::MultiNumericEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const MultiValueMapping<M>& mv_mapping, const EnumStoreT<T>& enum_store)
    : NumericRangeMatcher<T>(*qTerm),
      SearchContext(toBeSearched),
      _mv_mapping(mv_mapping),
      _enum_store(enum_store)
{
}

template <typename T, typename M>
Int64Range
MultiNumericEnumSearchContext<T, M>::getAsIntegerTerm() const
{
    return this->getRange();
}

template <typename T, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiNumericEnumSearchContext<T, M>::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
            ? std::make_unique<FilterAttributeIteratorStrict<MultiNumericEnumSearchContext>>(*this, matchData)
            : std::make_unique<FilterAttributeIteratorT<MultiNumericEnumSearchContext>>(*this, matchData);
    }
    return strict
        ? std::make_unique<AttributeIteratorStrict<MultiNumericEnumSearchContext>>(*this, matchData)
        : std::make_unique<AttributeIteratorT<MultiNumericEnumSearchContext>>(*this, matchData);
}

}
