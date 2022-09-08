// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_search_context.h"

namespace search::attribute {

template <typename Matcher>
NumericSearchContext<Matcher>::NumericSearchContext(const AttributeVector& to_be_searched, const QueryTermSimple& query_term, bool avoid_undefined_range)
    : SearchContext(to_be_searched),
      Matcher(query_term, avoid_undefined_range)
{
}

template <typename Matcher>
NumericSearchContext<Matcher>::NumericSearchContext(const AttributeVector& to_be_searched, Matcher &&matcher)
    : SearchContext(to_be_searched),
      Matcher(std::move(matcher))
{
}

template <typename Matcher>
Int64Range
NumericSearchContext<Matcher>::getAsIntegerTerm() const
{
    return Matcher::getRange();
}

template <typename Matcher>
DoubleRange
NumericSearchContext<Matcher>::getAsDoubleTerm() const
{
    return Matcher::getDoubleRange();
}

template <typename Matcher>
bool
NumericSearchContext<Matcher>::valid() const
{
    return Matcher::isValid();
}

}
