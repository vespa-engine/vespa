// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "metricvalueset.h"
#include <sstream>

namespace metrics {

template<typename ValueClass>
MetricValueSet<ValueClass>::MetricValueSet() noexcept
    : _values(),
      _activeValueIndex(0),
      _flags(0)
{ }

template<typename ValueClass>
MetricValueSet<ValueClass>::MetricValueSet(const MetricValueSet& rhs) noexcept
    : _values(rhs._values),
      _activeValueIndex(rhs._activeValueIndex.load(std::memory_order_relaxed)),
      _flags(rhs._flags.load(std::memory_order_relaxed))
{ }

template<typename ValueClass>
MetricValueSet<ValueClass> & MetricValueSet<ValueClass>::operator=(const MetricValueSet& other) noexcept
{
    setValues(other.getValues());
    return *this;
}

template<typename ValueClass>
ValueClass
MetricValueSet<ValueClass>::getValues() const {
    ValueClass v{};
    if (!isReset()) {
        // Must load with acquire to match release store in setValues.
        // Note that despite being atomic on _individual fields_, this
        // does not guarantee reading a consistent snapshot _across_
        // fields for any given metric.
        const size_t readIndex(_activeValueIndex.load(std::memory_order_acquire));
        v.relaxedLoadFrom(_values[readIndex]);
    }
    return v;
}

template<typename ValueClass>
bool
MetricValueSet<ValueClass>::setValues(const ValueClass& values) {
    // Only setter-thread can write _activeValueIndex, so relaxed
    // load suffices.
    uint32_t nextIndex = (_activeValueIndex.load(std::memory_order_relaxed) + 1) % _values.size();
    // Reset flag is loaded/stored with relaxed semantics since it does not
    // carry data dependencies. _activeValueIndex has a dependency on
    // _values, however, so we must ensure that stores are published
    // and loads acquired.
    if (isReset()) {
        removeFlag(RESET);
        ValueClass resetValues{};
        resetValues.relaxedStoreInto(_values[nextIndex]);
        _activeValueIndex.store(nextIndex, std::memory_order_release);
        return false;
    } else {
        values.relaxedStoreInto(_values[nextIndex]);
        _activeValueIndex.store(nextIndex, std::memory_order_release);
        return true;
    }
}

template<typename ValueClass>
std::string
MetricValueSet<ValueClass>::toString() {
    std::ostringstream ost;
    ost << "MetricValueSet(reset=" << (isReset() ? "true" : "false")
        << ", active " << _activeValueIndex;
#if 0
    ost << "\n  empty: " << ValueClass().toString("unknown");
    for (uint32_t i=0; i<_values.size(); ++i) {
        ost << "\n  " << _values[i].toString("unknown");
    }
#endif
    ost << "\n)";
    return ost.str();
}

} // metrics

