// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/vespalib/util/sync.h>

namespace proton {

struct FeedMetrics : metrics::MetricSet
{
    vespalib::Lock               updateLock;
    metrics::LongCountMetric     count;
    metrics::DoubleAverageMetric latency;

    FeedMetrics();
    ~FeedMetrics();
};

class PerDocTypeFeedMetrics : metrics::MetricSet {
    vespalib::Lock _update_lock;
    metrics::LongCountMetric _puts;
    metrics::LongCountMetric _updates;
    metrics::LongCountMetric _removes;
    metrics::LongCountMetric _moves;
    metrics::DoubleAverageMetric _put_latency;
    metrics::DoubleAverageMetric _update_latency;
    metrics::DoubleAverageMetric _remove_latency;
    metrics::DoubleAverageMetric _move_latency;

public:
    PerDocTypeFeedMetrics(metrics::MetricSet *parent);
    ~PerDocTypeFeedMetrics();
    void RegisterPut(const FastOS_Time &start_time);
    void RegisterUpdate(const FastOS_Time &start_time);
    void RegisterRemove(const FastOS_Time &start_time);
    void RegisterMove(const FastOS_Time &start_time);
};

} // namespace proton

