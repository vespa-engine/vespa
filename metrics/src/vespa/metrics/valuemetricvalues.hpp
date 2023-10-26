// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "valuemetricvalues.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <ostream>

namespace metrics {

using vespalib::IllegalArgumentException;

template<typename AvgVal, typename TotVal>
ValueMetricValues<AvgVal, TotVal>::ValueMetricValues()
    : _count(0),
      _min(std::numeric_limits<AvgVal>::max()),
      _max(std::numeric_limits<AvgVal>::min()),
      _last(0),
      _total(0)
{
        // numeric_limits min() returns smallest positive number
        // for signed floating point types. Haven't found a way to
        // get minimum negative value, so using -1 * positive for those.
    if (std::numeric_limits<AvgVal>::is_signed
        && !std::numeric_limits<AvgVal>::is_integer)
    {
        _max = -1 * std::numeric_limits<AvgVal>::max();
    }
}

template<typename AvgVal, typename TotVal>
void ValueMetricValues<AvgVal, TotVal>::relaxedStoreInto(AtomicImpl& target) const noexcept {
    target._count.store(_count, std::memory_order_relaxed);
    target._min.store(_min, std::memory_order_relaxed);
    target._max.store(_max, std::memory_order_relaxed);
    target._last.store(_last, std::memory_order_relaxed);
    target._total.store(_total, std::memory_order_relaxed);
}

template<typename AvgVal, typename TotVal>
void ValueMetricValues<AvgVal, TotVal>::relaxedLoadFrom(const AtomicImpl& source) noexcept {
    _count = source._count.load(std::memory_order_relaxed);
    _min = source._min.load(std::memory_order_relaxed);
    _max = source._max.load(std::memory_order_relaxed);
    _last = source._last.load(std::memory_order_relaxed);
    _total = source._total.load(std::memory_order_relaxed);
}

template<typename AvgVal, typename TotVal>
template<typename T>
T ValueMetricValues<AvgVal, TotVal>::getValue(stringref id) const {
    if (id == "last") return static_cast<T>(_last);
    if (id == "count") return static_cast<T>(_count);
    if (id == "total") return static_cast<T>(_total);
    if (id == "min") return static_cast<T>(_count > 0 ? _min : 0);
    if (id == "max") return static_cast<T>( _count > 0 ? _max : 0);
    throw IllegalArgumentException("No value " + vespalib::string(id) + " in value metric.", VESPA_STRLOC);
}

template<typename AvgVal, typename TotVal>
double ValueMetricValues<AvgVal, TotVal>::getDoubleValue(stringref id) const {
    return getValue<double>(id);
}
template<typename AvgVal, typename TotVal>
uint64_t ValueMetricValues<AvgVal, TotVal>::getLongValue(stringref id) const {
    return getValue<uint64_t>(id);
}
template<typename AvgVal, typename TotVal>
void ValueMetricValues<AvgVal, TotVal>::output(const std::string& id, std::ostream& out) const {
    if (id == "last") { out << _last; return; }
    if (id == "count") { out << _count; return; }
    if (id == "total") { out << _total; return; }
    if (id == "min") { out << (_count > 0 ? _min : 0); return; }
    if (id == "max") { out << (_count > 0 ? _max : 0); return; }
    throw IllegalArgumentException("No value " + id + " in value metric.", VESPA_STRLOC);
}
template<typename AvgVal, typename TotVal>
void ValueMetricValues<AvgVal, TotVal>::output(const std::string& id, vespalib::JsonStream& stream) const {
    if (id == "last") { stream << _last; return; }
    if (id == "count") { stream << _count; return; }
    if (id == "total") { stream << _total; return; }
    if (id == "min") { stream << (_count > 0 ? _min : 0); return; }
    if (id == "max") { stream << (_count > 0 ? _max : 0); return; }
    throw IllegalArgumentException("No value " + id + " in value metric.", VESPA_STRLOC);
}

template<typename AvgVal, typename TotVal>
std::ostream & operator << (std::ostream & os, const ValueMetricValues<AvgVal, TotVal> & v) {
    os << "count=" << v._count;
    os << ", total=" << v._total;
    return os;
}

} // metrics

