// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include "stable_store.h"
#include "counter.h"
#include "gauge.h"

namespace vespalib {
namespace metrics {

// internal
struct CurrentSamples {
    std::mutex lock;
    StableStore<Counter::Increment> counterIncrements;
    StableStore<Gauge::Measurement> gaugeMeasurements;

    ~CurrentSamples() {}

    void add(Counter::Increment inc) {
        std::lock_guard<std::mutex> guard(lock);
        counterIncrements.add(inc);
    }
    void sample(Gauge::Measurement value) {
        std::lock_guard<std::mutex> guard(lock);
        gaugeMeasurements.add(value);
    }
};

void swap(CurrentSamples& a, CurrentSamples& b);

} // namespace vespalib::metrics
} // namespace vespalib
