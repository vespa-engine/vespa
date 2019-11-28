// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numericbase.h"
#include <vespa/searchlib/query/query_term_simple.h>

namespace search {

template<typename T>
NumericAttribute::Equal<T>::Equal(const QueryTermSimple &queryTerm, bool avoidUndefinedInRange)
    : _value(0),
      _valid(false)
{
    (void) avoidUndefinedInRange;
    QueryTermSimple::RangeResult<T> res = queryTerm.getRange<T>();
    _valid = res.valid && res.isEqual() && !res.adjusted;
    _value = res.high;
}

template<typename T>
NumericAttribute::Range<T>::Range(const QueryTermSimple & queryTerm, bool avoidUndefinedInRange)
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
