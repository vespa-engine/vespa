// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/vespalib/util/sync.h>

namespace search::engine {

struct TransportMetrics : metrics::MetricSet
{
    struct QueryMetrics : metrics::MetricSet {
        metrics::LongCountMetric     count;
        metrics::DoubleAverageMetric latency;

        QueryMetrics(metrics::MetricSet *parent);
        ~QueryMetrics();
    };

    struct DocsumMetrics : metrics::MetricSet {
        metrics::LongCountMetric     count;
        metrics::LongCountMetric     docs;
        metrics::DoubleAverageMetric latency;

        DocsumMetrics(metrics::MetricSet *parent);
        ~DocsumMetrics();
    };

    vespalib::Lock updateLock;
    QueryMetrics   query;
    DocsumMetrics  docsum;

    TransportMetrics();
    ~TransportMetrics() override;
};

}

