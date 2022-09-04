// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/range.h>
#include <cstddef>

namespace search { class QueryTermSimple; }

namespace search::attribute {

/*
 * Class used to determine if an attribute vector value is a match for
 * the query range.
 */
template<typename T>
class NumericRangeMatcher
{
protected:
    T _low;
    T _high;
private:
    bool _valid;
    int _limit;
    size_t _max_per_group;
public:
    NumericRangeMatcher(const QueryTermSimple& queryTerm, bool avoidUndefinedInRange=false);
protected:
    Int64Range getRange() const {
        return {static_cast<int64_t>(_low), static_cast<int64_t>(_high)};
    }
    DoubleRange getDoubleRange() const {
        return {static_cast<double>(_low), static_cast<double>(_high)};
    }
    bool isValid() const { return _valid; }
    bool match(T v) const { return (_low <= v) && (v <= _high); }
    int getRangeLimit() const { return _limit; }
    size_t getMaxPerGroup() const { return _max_per_group; }

    template <typename BaseType>
    search::Range<BaseType>
    cappedRange(bool isFloat)
    {
        BaseType low = static_cast<BaseType>(_low);
        BaseType high = static_cast<BaseType>(_high);

        BaseType numMin = std::numeric_limits<BaseType>::min();
        BaseType numMax = std::numeric_limits<BaseType>::max();

        if (isFloat) {
            if (_low <= (-numMax)) {
                low = -numMax;
            }
        } else {
            if (_low <= (numMin)) {
                low = numMin + 1; // we must avoid the undefined value
            }
        }

        if (_high >= (numMax)) {
            high = numMax;
        }
        return search::Range<BaseType>(low, high);
    }
};

}
