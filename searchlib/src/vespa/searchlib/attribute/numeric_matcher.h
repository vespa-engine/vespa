// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/range.h>

namespace search { class QueryTermSimple; }

namespace search::attribute {

/*
 * Class used to determine if an attribute vector value is a match for
 * the query value.
 */
template<typename T>
class NumericMatcher
{
private:
    T _value;
    bool _valid;
protected:
    NumericMatcher(const QueryTermSimple& queryTerm, bool avoidUndefinedInRange);
    bool isValid() const { return _valid; }
    bool match(T v) const { return v == _value; }
    Int64Range getRange() const {
        return {static_cast<int64_t>(_value)};
    }
    DoubleRange getDoubleRange() const {
        return {static_cast<double>(_value)};
    }
};

}
