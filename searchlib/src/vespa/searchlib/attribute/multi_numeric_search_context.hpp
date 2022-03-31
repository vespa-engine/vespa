// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_numeric_search_context.h"
#include "attributeiterators.hpp"
#include "multi_value_mapping.h"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

template <typename T, typename M>
bool
MultiNumericSearchContext<T, M>::valid() const
{
    return this->isValid();
}

template <typename T, typename M>
MultiNumericSearchContext<T, M>::MultiNumericSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const MultiValueMapping<M>& mv_mapping)
    : attribute::NumericRangeMatcher<T>(*qTerm),
      attribute::SearchContext(toBeSearched),
      _mv_mapping(mv_mapping)
{
}

template <typename T, typename M>
Int64Range
MultiNumericSearchContext<T, M>::getAsIntegerTerm() const
{
    return this->getRange();
}

template <typename T, typename M>
std::unique_ptr<queryeval::SearchIterator>
MultiNumericSearchContext<T, M>::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
            ? std::make_unique<FilterAttributeIteratorStrict<MultiNumericSearchContext<T, M>>>(*this, matchData)
            : std::make_unique<FilterAttributeIteratorT<MultiNumericSearchContext<T, M>>>(*this, matchData);
    }
    return strict
        ? std::make_unique<AttributeIteratorStrict<MultiNumericSearchContext<T, M>>>(*this, matchData)
        : std::make_unique<AttributeIteratorT<MultiNumericSearchContext<T, M>>>(*this, matchData);
}

}
