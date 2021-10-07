// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <mutex>
#include "stable_store.h"
#include "metric_id.h"
#include "point.h"
#include "counter.h"
#include "gauge.h"
#include "clock.h"
#include "counter_aggregator.h"
#include "gauge_aggregator.h"
#include "current_samples.h"

namespace vespalib {
namespace metrics {

// internal
struct Bucket {
    size_t genCnt;
    TimeStamp startTime;
    TimeStamp endTime;
    std::vector<CounterAggregator> counters;
    std::vector<GaugeAggregator> gauges;

    void merge(const CurrentSamples &other);
    void merge(const Bucket &other);
    void padMetrics(const Bucket &source);

    Bucket(size_t generation, TimeStamp started, TimeStamp ended)
        : genCnt(generation),
          startTime(started),
          endTime(ended),
          counters(),
          gauges()
    {}
    ~Bucket() {}
    Bucket(Bucket &&) = default;
    Bucket(const Bucket &) = default;
    Bucket& operator= (Bucket &&) = default;
};

} // namespace vespalib::metrics
} // namespace vespalib
