// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "valuemetric.h"
#include "memoryconsumption.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>

namespace metrics {

template<typename AvgVal, typename TotVal, bool SumOnAdd>
ValueMetric<AvgVal, TotVal, SumOnAdd>::ValueMetric(
        const String& name, const String& tags,
        const String& description, MetricSet* owner)
    : AbstractValueMetric(name, tags, description, owner),
      _values()
{
    _values.setFlag(LOG_IF_UNSET);
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
ValueMetric<AvgVal, TotVal, SumOnAdd>::ValueMetric(
        const String& name, const Tags dimensions,
        const String& description, MetricSet* owner)
    : AbstractValueMetric(name, std::move(dimensions), description, owner),
      _values()
{
    _values.setFlag(LOG_IF_UNSET);
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
ValueMetric<AvgVal, TotVal, SumOnAdd>::ValueMetric(
        const ValueMetric<AvgVal, TotVal, SumOnAdd>& other,
        CopyType copyType, MetricSet* owner)
    : AbstractValueMetric(other, owner),
      _values(other._values, copyType == CLONE ? other._values.size() : 1)
{
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
ValueMetric<AvgVal, TotVal, SumOnAdd>::~ValueMetric() { }

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void ValueMetric<AvgVal, TotVal, SumOnAdd>::inc(AvgVal incVal)
{
    if (!checkFinite(incVal, std::is_floating_point<AvgVal>())) {
        return;
    }
    Values values;
    do {
        values = _values.getValues();
        AvgVal val = values._last + incVal;
        ++values._count;
        values._total += val;
        if (val < values._min) values._min = val;
        if (val > values._max) values._max = val;
        values._last = val;
    } while (!_values.setValues(values));
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void ValueMetric<AvgVal, TotVal, SumOnAdd>::dec(AvgVal decVal)
{
    if (!checkFinite(decVal, std::is_floating_point<AvgVal>())) {
        return;
    }
    Values values;
    do {
        values = _values.getValues();
        AvgVal val = values._last - decVal;
        ++values._count;
        values._total += val;
        if (val < values._min) values._min = val;
        if (val > values._max) values._max = val;
        values._last = val;
    } while (!_values.setValues(values));
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void
ValueMetric<AvgVal, TotVal, SumOnAdd>::addToSnapshot(
        Metric& other, std::vector<Metric::UP> &) const
{
    ValueMetric<AvgVal, TotVal, SumOnAdd>& o(
            reinterpret_cast<ValueMetric<AvgVal, TotVal, SumOnAdd>&>(other));
    if (_values.getValues()._count == 0) return; // Don't add if not set
    o.add(_values.getValues(), false);
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void
ValueMetric<AvgVal, TotVal, SumOnAdd>::addToPart(Metric& other) const
{
    ValueMetric<AvgVal, TotVal, SumOnAdd>& o(
            reinterpret_cast<ValueMetric<AvgVal, TotVal, SumOnAdd>&>(
                other));
    o.add(_values.getValues(), SumOnAdd);
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void
ValueMetric<AvgVal, TotVal, SumOnAdd>::add(const Values& values2, bool sumOnAdd)
{
    bool overflow;
    Values values;
    do {
        values = _values.getValues();
        overflow = values._count > values._count + values2._count
                || (values2._total >= 0
                    ? values._total > values._total + values2._total
                    : values._total < values._total + values2._total);
        if (values._count == 0) {
            values = values2;
        } else if (values2._count == 0) {
            // Do nothing
        } else if (sumOnAdd) {
            double totalAverage
                    = static_cast<double>(values._total) / values._count
                    + static_cast<double>(values2._total) / values2._count;
            values._count += values2._count;
            values._total = static_cast<TotVal>(totalAverage * values._count);
            values._last += values2._last;
            _values.setFlag(SUMMED_AVERAGE);
        } else {
            values._count += values2._count;
            values._total += values2._total;
            values._last = values2._last;
        }
        if (values._min > values2._min) values._min = values2._min;
        if (values._max < values2._max) values._max = values2._max;
    } while (!_values.setValues(values));
    if (overflow) {
        _values.reset();
        logWarning("Overflow", "add");
    }
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void
ValueMetric<AvgVal, TotVal, SumOnAdd>::dec(const Values& values2)
{
    bool underflow;
    Values values;
    do {
        values = _values.getValues();
        underflow = values._count < values._count - values2._count
                || values._total < values._total - values2._total;
        values._count -= values2._count;
        values._total -= values2._total;
    } while (!_values.setValues(values));
    if (underflow) {
        _values.reset();
        logWarning("Underflow", "dec");
    }
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void ValueMetric<AvgVal, TotVal, SumOnAdd>::addValueWithCount(
        AvgVal avg, TotVal tot, uint32_t count, AvgVal min, AvgVal max)
{
    if (!checkFinite(avg, std::is_floating_point<AvgVal>())) {
        return;
    }
    Values values;
    do {
        values = _values.getValues();
        values._count += count;
        values._total += tot;
        if (min < values._min) values._min = min;
        if (max > values._max) values._max = max;
        values._last = avg;
    } while (!_values.setValues(values));
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
ValueMetric<AvgVal, TotVal, SumOnAdd>&
ValueMetric<AvgVal, TotVal, SumOnAdd>::operator+=(
        const ValueMetric<AvgVal, TotVal, SumOnAdd>& other)
{
    add(other._values.getValues(), SumOnAdd);
    return *this;
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
double
ValueMetric<AvgVal, TotVal, SumOnAdd>::getAverage() const
{
    Values values(_values.getValues());
    if (values._count == 0) return 0;
    return static_cast<double>(values._total) / values._count;
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
bool
ValueMetric<AvgVal, TotVal, SumOnAdd>::logEvent(const String& fullName) const
{
    Values values(_values.getValues());
    if (!logIfUnset() && !inUse(values)) return false;
    sendLogEvent(fullName, SumOnAdd
            ? static_cast<double>(values._last)
            : static_cast<double>(values._total) / values._count);
    return true;
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void
ValueMetric<AvgVal, TotVal, SumOnAdd>::print(
        std::ostream& out, bool verbose, const std::string& indent,
        uint64_t secondsPassed) const
{
    (void) indent;
    (void) secondsPassed;
    Values values(_values.getValues());
    if (!inUse(values) && !verbose) return;
    out << this->_name << " average=" << (values._count == 0
            ? 0 : static_cast<double>(values._total) / values._count)
        << " last=" << values._last;
    if (!summedAverage()) {
        if (values._count > 0) {
            out << " min=" << values._min << " max=" << values._max;
        }
        out << " count=" << values._count << " total=" << values._total;
    }
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
int64_t
ValueMetric<AvgVal, TotVal, SumOnAdd>::getLongValue(const stringref & id) const
{
    Values values(_values.getValues());
    if (id == "last" || (SumOnAdd && id == "value"))
        return static_cast<int64_t>(values._last);
    if (id == "average" || (!SumOnAdd && id == "value"))
        return static_cast<int64_t>(getAverage());
    if (id == "count") return static_cast<int64_t>(values._count);
    if (id == "total") return static_cast<int64_t>(values._total);
    if (id == "min") return static_cast<int64_t>(
            values._count > 0 ? values._min : 0);
    if (id == "max") return static_cast<int64_t>(
            values._count > 0 ? values._max : 0);
    throw vespalib::IllegalArgumentException(
            "No value " + id + " in average metric.", VESPA_STRLOC);
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
double
ValueMetric<AvgVal, TotVal, SumOnAdd>::getDoubleValue(const stringref & id) const
{
    Values values(_values.getValues());
    if (id == "last" || (SumOnAdd && id == "value"))
        return static_cast<double>(values._last);
    if (id == "average" || (!SumOnAdd && id == "value"))
        return getAverage();
    if (id == "count") return static_cast<double>(values._count);
    if (id == "total") return static_cast<double>(values._total);
    if (id == "min") return static_cast<double>(
            values._count > 0 ? values._min : 0);
    if (id == "max") return static_cast<double>(
            values._count > 0 ? values._max : 0);
    throw vespalib::IllegalArgumentException(
            "No value " + vespalib::string(id) + " in average metric.", VESPA_STRLOC);
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void
ValueMetric<AvgVal, TotVal, SumOnAdd>::addMemoryUsage(
        MemoryConsumption& mc) const
{
    ++mc._valueMetricCount;
    mc._valueMetricValues += _values.getMemoryUsageAllocatedInternally();
    mc._valueMetricMeta += sizeof(ValueMetric<AvgVal, TotVal, SumOnAdd>)
                         - sizeof(Metric);
    Metric::addMemoryUsage(mc);
}

template<typename AvgVal, typename TotVal, bool SumOnAdd>
void
ValueMetric<AvgVal, TotVal, SumOnAdd>::printDebug(
        std::ostream& out, const std::string& indent) const
{
    Values values(_values.getValues());
    out << "value=" << values._last << " ";
    Metric::printDebug(out, indent);
}

} // metrics

