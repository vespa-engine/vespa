// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "search_context.h"

namespace search::attribute {

/*
 * NumericSearchContext is an abstract base class for search contexts
 * handling a query term on a numeric attribute vector.
 */
template <typename Matcher>
class NumericSearchContext : public SearchContext, public Matcher
{
protected:
    using MatcherType = Matcher;
public:
    NumericSearchContext(const AttributeVector& to_be_searched, const QueryTermSimple& query_term, bool avoid_undefined_range);
    NumericSearchContext(const AttributeVector& to_be_searched, Matcher&& matcher);
    Int64Range getAsIntegerTerm() const override;
    DoubleRange getAsDoubleTerm() const override;
    bool valid() const override;
};

}
