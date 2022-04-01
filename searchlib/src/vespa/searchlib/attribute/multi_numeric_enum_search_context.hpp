// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_numeric_enum_search_context.h"
#include "multi_enum_search_context.hpp"

namespace search::attribute {

template <typename T, typename M>
MultiNumericEnumSearchContext<T, M>::MultiNumericEnumSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const AttributeVector& toBeSearched, const MultiValueMapping<M>& mv_mapping, const EnumStoreT<T>& enum_store)
    : MultiEnumSearchContext<T, NumericRangeMatcher<T>, M>(NumericRangeMatcher<T>(*qTerm), toBeSearched, mv_mapping, enum_store)
{
}

template <typename T, typename M>
Int64Range
MultiNumericEnumSearchContext<T, M>::getAsIntegerTerm() const
{
    return this->getRange();
}

}
