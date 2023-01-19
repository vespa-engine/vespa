// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include <vector>
#include <atomic>
#include <array>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/jsonstream.h>

namespace metrics {

struct MetricValueClass {
    using UP = std::unique_ptr<MetricValueClass>;
    using stringref = vespalib::stringref;
    virtual ~MetricValueClass() {}

    virtual double getDoubleValue(stringref id) const = 0;
    virtual uint64_t getLongValue(stringref id) const = 0;
    virtual void output(const std::string& id, std::ostream&) const = 0;
    virtual void output(const std::string& id, vespalib::JsonStream&) const = 0;
    std::string toString(const std::string& id);
};

template<typename ValueClass>
class MetricValueSet {
    using AtomicValues = typename ValueClass::AtomicImpl;
    std::array<AtomicValues, 3> _values;
    std::atomic<uint32_t>       _activeValueIndex;
    std::atomic<uint32_t>       _flags;

    enum Flag { RESET = 1 };
    bool isReset() const { return hasFlag(RESET); }
public:
    MetricValueSet() noexcept;
    MetricValueSet(const MetricValueSet&) noexcept;
    MetricValueSet& operator=(const MetricValueSet& other) noexcept;

    /** Get the current values. */
    ValueClass getValues() const;

    /**
     * Set the current values for the metric. This function should not be
     * called in parallel. Only call it from a single thread or use external
     * locking. If it returns false, it means the metric have just been reset.
     * In which case, redo getValues(), apply the update again, and call
     * setValues() again.
     */
    bool setValues(const ValueClass& values);

    void reset() {
        setFlag(RESET);
    }

    std::string toString();

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
};

} // metrics

