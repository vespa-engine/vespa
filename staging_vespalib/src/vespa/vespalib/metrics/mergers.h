// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simple_metrics.h"

namespace vespalib {
namespace metrics {

// internal
struct MergedCounter {
    int idx;
    size_t count;
    MergedCounter(int idx);
    void merge(const CounterIncrement &other);
    void merge(const MergedCounter &other);
};

// internal
struct MergedGauge {
    int idx;
    size_t observedCount;
    double sumValue;
    double minValue;
    double maxValue;
    double lastValue;
    MergedGauge(int idx);
    void merge(const GaugeMeasurement &other);
    void merge(const MergedGauge &other);
};

// internal
struct CurrentSamples {
    std::vector<CounterIncrement> counterIncrements;
    std::vector<GaugeMeasurement> gaugeMeasurements;
};

// internal
struct Bucket {
    clock::time_point _startTime;
    clock::time_point _endedTime;
    std::vector<MergedCounter> counters;
    std::vector<MergedGauge> gauges;
    void merge(const CurrentSamples &other);
    void merge(const Bucket &other);
};

} // namespace vespalib::metrics
} // namespace vespalib
