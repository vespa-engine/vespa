// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_matcher.h"
#include <vespa/searchlib/query/query_term_simple.h>

namespace search::attribute {

template<typename T>
NumericMatcher<T>::NumericMatcher(const QueryTermSimple& queryTerm, bool avoidUndefinedInRange)
    : _value(0),
      _valid(false)
{
    (void) avoidUndefinedInRange;
    QueryTermSimple::RangeResult<T> res = queryTerm.getRange<T>();
    _valid = res.valid && res.isEqual() && !res.adjusted;
    _value = res.high;
}

}
