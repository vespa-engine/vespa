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
 * Note that aggregation requires sample interval to be the same(similar) for all samples.
 **/
class ExecutorStats {
private:
    size_t   _threadCount;
    double   _absUtil;
public:
    using QueueSizeT = AggregatedAverage<size_t>;
    QueueSizeT queueSize;
    size_t     acceptedTasks;
    size_t     rejectedTasks;
    size_t     wakeupCount; // Number of times a worker was woken up,

    ExecutorStats() : ExecutorStats(QueueSizeT(), 0, 0, 0) {}
    ExecutorStats(QueueSizeT queueSize_in, size_t accepted, size_t rejected, size_t wakeupCount_in)
        : _threadCount(1),
          _absUtil(1.0),
          queueSize(queueSize_in),
          acceptedTasks(accepted),
          rejectedTasks(rejected),
          wakeupCount(wakeupCount_in)
    {}
    void aggregate(const ExecutorStats & rhs) {
        _threadCount += rhs._threadCount;
        queueSize = QueueSizeT(queueSize.count() + rhs.queueSize.count(),
                               queueSize.total() + rhs.queueSize.total(),
                               queueSize.min() + rhs.queueSize.min(),
                               queueSize.max() + rhs.queueSize.max());
        acceptedTasks += rhs.acceptedTasks;
        rejectedTasks += rhs.rejectedTasks;
        wakeupCount += rhs.wakeupCount;
        _absUtil += rhs._absUtil;
    }
    ExecutorStats & setUtil(uint32_t threadCount, double idle) {
        _threadCount = threadCount;
        _absUtil = (1.0 - idle) * threadCount;
        return *this;
    }
    double getUtil() const { return _absUtil / _threadCount; }
    size_t getThreadCount() const { return _threadCount; }
};

}

