// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class MetricValueSet
 * \ingroup metrics
 *
 * \brief Utility for doing lockless metric updates and reads.
 *
 * We don't want to use regular locking while updating metrics due to overhead.
 * We use this class to make metric updates as safe as possible without
 * requiring locks.
 *
 * It keeps the set of values a metric wants to set. Thus it is templated on
 * the class keeping the values. All that is required of this class is that it
 * has an empty constructor and a copy constructor.
 *
 * The locking works, by keeping a set of values, with an active pointer into
 * the value vector. Assuming only one thread calls setValues(), it can update
 * the active pointer safely. We assume updating the active pointer is a
 * non-interruptable operations, such that other threads will see either the new
 * or the old value correctly. This should be the case on our platforms.
 *
 * Due to the reset functionality, it is possible to miss out on a metrics
 * added during the reset, but this is very unlikely. For that to happen, when
 * someone sets the reset flag, the writer thread must be in setValues(),
 * having already passed the check for the reset flag, but not finished setting
 * the values yet.
 */
#pragma once

#include <sstream>
#include <vector>
#include <atomic>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/jsonstream.h>

namespace metrics {

struct MetricValueClass {
    typedef std::unique_ptr<MetricValueClass> UP;
    virtual ~MetricValueClass() {}

    virtual double getDoubleValue(const vespalib::stringref & id) const = 0;
    virtual uint64_t getLongValue(const vespalib::stringref & id) const = 0;
    virtual void output(const std::string& id, std::ostream&) const = 0;
    virtual void output(const std::string& id, vespalib::JsonStream&) const = 0;
    std::string toString(const std::string& id) {
        std::ostringstream ost;
        output(id, ost);
        return ost.str();
    }
};

template<typename ValueClass>
class MetricValueSet {
    using AtomicValues = typename ValueClass::AtomicImpl;
    std::vector<AtomicValues> _values;
    std::atomic<uint32_t> _activeValueIndex;
    std::atomic<uint32_t> _flags;

    enum Flag { RESET = 1 };
    bool isReset() const { return hasFlag(RESET); }

    void validateCorrectValueSuperClass(const MetricValueClass&) {}

public:
    MetricValueSet(uint32_t copyCount = 3)
        : _values(copyCount),
          _activeValueIndex(0),
          _flags(0)
    {
    }

    MetricValueSet(const MetricValueSet& other, uint32_t copyCount = 3)
        : _values(copyCount),
          _activeValueIndex(0),
          _flags(other._flags.load(std::memory_order_relaxed))
    {
        setValues(other.getValues());
    }

    MetricValueSet& operator=(const MetricValueSet& other)
    {
        setValues(other.getValues());
        return *this;
    }


    /** Get the current values. */
    ValueClass getValues() const {
        ValueClass v{};
        if (!isReset()) {
            // Must load with acquire to match release store in setValues.
            // Note that despite being atomic on _individual fields_, this
            // does not guarantee reading a consistent snapshot _across_
            // fields for any given metric.
            const size_t readIndex(
                    _activeValueIndex.load(std::memory_order_acquire));
            v.relaxedLoadFrom(_values[readIndex]);
        }
        return v;
    }

    /**
     * Get the current values from the metric. This function should not be
     * called in parallel. Only call it from a single thread or use external
     * locking. If it returns false, it means the metric have just been reset.
     * In which case, redo getValues(), apply the update again, and call
     * setValues() again.
     */
    bool setValues(const ValueClass& values) {
        validateCorrectValueSuperClass(values);
        // Only setter-thread can write _activeValueIndex, so relaxed
        // load suffices.
        uint32_t nextIndex = (_activeValueIndex.load(std::memory_order_relaxed)
                              + 1) % _values.size();
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

    /**
     * Retrieve and reset in a single operation, to minimize chance of
     * alteration in the process.
     */
    ValueClass getValuesAndReset() {
        ValueClass result(getValues());
        setFlag(RESET);
        return result;
    }

    void reset() {
        setFlag(RESET);
    }

    std::string toString() {
        std::ostringstream ost;
        ost << "MetricValueSet(reset=" << (isReset() ? "true" : "false")
            << ", active " << _activeValueIndex;
        ost << "\n  empty: " << ValueClass().toString();
        for (uint32_t i=0; i<_values.size(); ++i) {
            ost << "\n  " << _values[i].toString();
        }
        ost << "\n)";
        return ost.str();
    }

    uint32_t getMemoryUsageAllocatedInternally() const {
        return _values.capacity() * sizeof(ValueClass);
    }

    uint32_t size() const { return _values.size(); }

    bool hasFlag(uint32_t flags) const {
        return ((_flags.load(std::memory_order_relaxed) & flags) != 0);
    }
    void setFlag(uint32_t flags) {
        _flags.store(_flags.load(std::memory_order_relaxed) | flags,
                     std::memory_order_relaxed);
    }
    void removeFlag(uint32_t flags) {
        _flags.store(_flags.load(std::memory_order_relaxed) & ~flags,
                     std::memory_order_relaxed);
    }
    uint32_t getFlags() const {
        return _flags.load(std::memory_order_relaxed);
    }
};

} // metrics

