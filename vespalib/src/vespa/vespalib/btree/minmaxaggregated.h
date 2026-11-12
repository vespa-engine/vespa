// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <limits>

namespace vespalib::btree {

/**
 * Aggregated min and max of the values (data) in a btree.
 * Used with integer (int32_t) or floating point (float) values.
 *
 * Floating point values are expected to be finite; NaN and infinities
 * are not expected to be stored. If they are, the aggregate can be
 * inexact:
 *
 * - The empty aggregate has min = numeric_limits<T>::max() and
 *   max = numeric_limits<T>::lowest(), i.e. the largest and smallest
 *   finite values, not +/-infinity. Since +inf never compares less than
 *   max(), min is reported as max() (not +inf) when every non-NaN value
 *   is +inf. Likewise, max is reported as lowest() (not -inf) when every
 *   non-NaN value is -inf. Otherwise infinities are reflected in min
 *   and max.
 *
 * - NaN compares false with everything, so adding a NaN value has no
 *   effect and NaN values are never reflected in min and max. An update
 *   from the current min or max value to NaN does not request
 *   recalculation and leaves min and max unchanged, so they still
 *   reflect the old value.
 */
template <typename T> class MinMaxAggregatedT {
    T _min;
    T _max;

public:
    using ValueType = T;

    MinMaxAggregatedT() : _min(std::numeric_limits<T>::max()), _max(std::numeric_limits<T>::lowest()) {}

    MinMaxAggregatedT(T min, T max) : _min(min), _max(max) {}

    T getMin() const { return _min; }
    T getMax() const { return _max; }

    bool operator==(const MinMaxAggregatedT& rhs) const { return ((_min == rhs._min) && (_max == rhs._max)); }

    bool operator!=(const MinMaxAggregatedT& rhs) const { return ((_min != rhs._min) || (_max != rhs._max)); }

    void add(T val) {
        if (_min > val) {
            _min = val;
        }
        if (_max < val) {
            _max = val;
        }
    }

    void add(const MinMaxAggregatedT& ca) {
        if (_min > ca._min) {
            _min = ca._min;
        }
        if (_max < ca._max) {
            _max = ca._max;
        }
    }

    void add(const MinMaxAggregatedT& oldca, const MinMaxAggregatedT& ca) {
        (void)oldca;
        add(ca);
    }

    /* Returns true if recalculation is needed */
    bool remove(T val) { return (_min == val || _max == val); }

    /* Returns true if recalculation is needed */
    bool remove(const MinMaxAggregatedT& oldca, const MinMaxAggregatedT& ca) {
        return (_min == oldca._min && _min != ca._min) || (_max == oldca._max && _max != ca._max);
    }

    /* Returns true if recalculation is needed */
    bool update(T oldVal, T val) {
        if ((_min == oldVal && _min < val) || (_max == oldVal && _max > val)) {
            return true;
        }
        add(val);
        return false;
    }

    /* Returns true if recalculation is needed */
    bool update(const MinMaxAggregatedT& oldca, const MinMaxAggregatedT& ca) {
        if ((_min == oldca._min && _min < ca._min) || (_max == oldca._max && _max > ca._max)) {
            return true;
        }
        add(ca);
        return false;
    }
};

using MinMaxAggregated = MinMaxAggregatedT<int32_t>;
using FloatMinMaxAggregated = MinMaxAggregatedT<float>;

} // namespace vespalib::btree
