// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <limits>
#include <cstdint>

namespace vespalib {

/**
 * Used for aggregating values, preserving min, max, sum and count.
 */
template <typename T>
class AggregatedAverage {
public:
    AggregatedAverage() : AggregatedAverage(0ul, T(0), std::numeric_limits<T>::max(), std::numeric_limits<T>::min()) { }
    explicit AggregatedAverage(T value) : AggregatedAverage(1, value, value, value) { }
    AggregatedAverage(size_t count_in, T total_in, T min_in, T max_in)
        : _count(count_in),
          _total(total_in),
          _min(min_in),
          _max(max_in)
    { }
    AggregatedAverage & operator += (const AggregatedAverage & rhs) {
        add(rhs);
        return *this;
    }
    void add(const AggregatedAverage & rhs) {
        add(rhs._count, rhs._total, rhs._min, rhs._max);
    }
    void add(T value) {
        add(1, value, value, value);
    }
    void add(size_t count_in, T total_in, T min_in, T max_in) {
        _count += count_in;
        _total += total_in;
        if (min_in < _min) _min = min_in;
        if (max_in > _max) _max = max_in;
    }
    size_t count() const { return _count; }
    T total() const { return _total; }
    T min() const { return _min; }
    T max() const { return _max; }
    double average() const { return (_count > 0) ? (double(_total) / _count) : 0; }
private:
    size_t _count;
    T      _total;
    T      _min;
    T      _max;
};

/**
 * Struct representing stats for an executor.
 **/
struct ExecutorStats {
    using QueueSizeT = AggregatedAverage<size_t>;
    uint32_t   executorCount;
    QueueSizeT queueSize;
    size_t     acceptedTasks;
    size_t     rejectedTasks;
    size_t     workingDays; // Number of time a worker showed up for work,
    double     dutyCycle;
    ExecutorStats() : ExecutorStats(1, QueueSizeT(), 0, 0, 0, 1.0) {}
    ExecutorStats(uint32_t executorCount_in, QueueSizeT queueSize_in, size_t accepted, size_t rejected, size_t wakeupCount, double dutyCycle_in)
        : executorCount(executorCount_in),
          queueSize(queueSize_in),
          acceptedTasks(accepted),
          rejectedTasks(rejected),
          workingDays(wakeupCount),
          dutyCycle(dutyCycle_in)
    {}
    ExecutorStats & operator += (const ExecutorStats & rhs) {
        executorCount += rhs.executorCount;
        queueSize = QueueSizeT(queueSize.count() + rhs.queueSize.count(),
                               queueSize.total() + rhs.queueSize.total(),
                               queueSize.min() + rhs.queueSize.min(),
                               queueSize.max() + rhs.queueSize.max());
        acceptedTasks += rhs.acceptedTasks;
        rejectedTasks += rhs.rejectedTasks;
        workingDays += rhs.workingDays;
        dutyCycle += rhs.dutyCycle;
        return *this;
    }
    double getNormalizedDutyCycle() const { return dutyCycle / executorCount; }
};

}

