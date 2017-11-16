// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include "simple_metrics.h"

namespace vespalib {
namespace metrics {

// internal
struct MergedCounter {
    size_t idx;
    size_t count;
    MergedCounter(size_t idx);
    void merge(const CounterIncrement &other);
    void merge(const MergedCounter &other);
};

// internal
struct MergedGauge {
    size_t idx;
    size_t observedCount;
    double sumValue;
    double minValue;
    double maxValue;
    double lastValue;

    MergedGauge(size_t idx);

    void merge(const GaugeMeasurement &other);
    void merge(const MergedGauge &other);
};

// internal
struct CurrentSamples {
    std::mutex lock;
    std::vector<CounterIncrement> counterIncrements;
    std::vector<GaugeMeasurement> gaugeMeasurements;

    void add(CounterIncrement inc) {
        std::lock_guard<std::mutex> guard(lock);
        counterIncrements.push_back(inc);
    }
    void sample(GaugeMeasurement value) {
        std::lock_guard<std::mutex> guard(lock);
        gaugeMeasurements.push_back(value);
    }
};

// internal
struct Bucket {
    clock::time_point startTime;
    clock::time_point endTime;
    std::vector<MergedCounter> counters;
    std::vector<MergedGauge> gauges;

    void merge(const CurrentSamples &other);
    void merge(const Bucket &other);

    Bucket(clock::time_point started, clock::time_point ended)
        : startTime(started),
          endTime(ended),
          counters(),
          gauges()
    {}
    ~Bucket() {}
};

void swap(CurrentSamples& a, CurrentSamples& b);
void swap(Bucket& a, Bucket& b);

} // namespace vespalib::metrics
} // namespace vespalib
