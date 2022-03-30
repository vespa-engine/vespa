// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "single_numeric_search_context.h"
#include "attributeiterators.hpp"
#include <vespa/searchlib/queryeval/emptysearch.h>

namespace search::attribute {

template <typename T, typename M>
bool
SingleNumericSearchContext<T, M>::valid() const
{
    return M::isValid();
}

template <typename T, typename M>
SingleNumericSearchContext<T, M>::SingleNumericSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const T* data)
    : M(*qTerm, true),
      attribute::SearchContext(toBeSearched),
      _data(data)
{
}

template <typename T, typename M>
Int64Range
SingleNumericSearchContext<T, M>::getAsIntegerTerm() const {
    return M::getRange();
}

template <typename T, typename M>
std::unique_ptr<queryeval::SearchIterator>
SingleNumericSearchContext<T, M>::createFilterIterator(fef::TermFieldMatchData* matchData, bool strict)
{
    if (!valid()) {
        return std::make_unique<queryeval::EmptySearch>();
    }
    if (getIsFilter()) {
        return strict
            ? std::make_unique<FilterAttributeIteratorStrict<SingleNumericSearchContext<T, M>>>(*this, matchData)
            : std::make_unique<FilterAttributeIteratorT<SingleNumericSearchContext<T, M>>>(*this, matchData);
    }
    return strict
        ? std::make_unique<AttributeIteratorStrict<SingleNumericSearchContext<T, M>>>(*this, matchData)
        : std::make_unique<AttributeIteratorT<SingleNumericSearchContext<T, M>>>(*this, matchData);
}

}
