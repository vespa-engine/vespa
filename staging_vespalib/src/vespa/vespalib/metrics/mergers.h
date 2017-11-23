// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include "no_realloc_bunch.h"
#include "metric_identifier.h"
#include "counter.h"
#include "gauge.h"
#include "clock.h"

namespace vespalib {
namespace metrics {

// internal
struct CounterAggregator {
    MetricIdentifier idx;
    size_t count;

    CounterAggregator(MetricIdentifier id);

    void merge(const CounterIncrement &other);
    void merge(const CounterAggregator &other);
};

// internal
struct GaugeAggregator {
    MetricIdentifier idx;
    size_t observedCount;
    double sumValue;
    double minValue;
    double maxValue;
    double lastValue;

    GaugeAggregator(MetricIdentifier id);

    void merge(const GaugeMeasurement &other);
    void merge(const GaugeAggregator &other);
};

// internal
struct CurrentSamples {
    std::mutex lock;
    NoReallocBunch<CounterIncrement> counterIncrements;
    NoReallocBunch<GaugeMeasurement> gaugeMeasurements;

    ~CurrentSamples() {}

    void add(CounterIncrement inc) {
        std::lock_guard<std::mutex> guard(lock);
        counterIncrements.add(inc);
    }
    void sample(GaugeMeasurement value) {
        std::lock_guard<std::mutex> guard(lock);
        gaugeMeasurements.add(value);
    }
};

// internal
struct Bucket {
    InternalTimeStamp startTime;
    InternalTimeStamp endTime;
    std::vector<CounterAggregator> counters;
    std::vector<GaugeAggregator> gauges;

    void merge(const CurrentSamples &other);
    void merge(const Bucket &other);

    Bucket(InternalTimeStamp started, InternalTimeStamp ended)
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
