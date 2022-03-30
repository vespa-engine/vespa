// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_range_matcher.h"
#include <vespa/searchlib/query/query_term_simple.h>

namespace search::attribute {

template<typename T>
NumericRangeMatcher<T>::NumericRangeMatcher(const QueryTermSimple& queryTerm, bool avoidUndefinedInRange)
    : _low(0),
      _high(0),
      _valid(false)
{
    QueryTermSimple::RangeResult<T> res = queryTerm.getRange<T>();
    _valid = res.isEqual() ? (res.valid && !res.adjusted) : res.valid;
    _low = res.low;
    _high = res.high;
    _limit = queryTerm.getRangeLimit();
    _max_per_group = queryTerm.getMaxPerGroup();
    if (_valid && avoidUndefinedInRange &&
        _low == std::numeric_limits<T>::min()) {
        _low += 1;
    }
}

}
