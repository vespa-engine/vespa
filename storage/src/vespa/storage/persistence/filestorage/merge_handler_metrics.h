// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/countmetric.h>

namespace metrics { class MetricSet; }
namespace storage {

// Provides a convenient wrapper for all MergeHandler-related metrics.
// This is _not_ its own MetricSet; metrics are owned by an explicitly provided
// parent. This is to prevent metric paths from changing, as external aggregation
// depends on the existing paths.
struct MergeHandlerMetrics {
    metrics::LongCountMetric bytesMerged;
    // Aggregate metrics:
    metrics::DoubleAverageMetric mergeLatencyTotal;
    metrics::DoubleAverageMetric mergeMetadataReadLatency;
    metrics::DoubleAverageMetric mergeDataReadLatency;
    metrics::DoubleAverageMetric mergeDataWriteLatency;
    metrics::DoubleAverageMetric mergeAverageDataReceivedNeeded;
    // Individual operation metrics. These capture both count and latency sum, so
    // no need for explicit count metric on the side.
    metrics::DoubleAverageMetric put_latency;
    metrics::DoubleAverageMetric remove_latency;
    // Iteration over metadata and document payload data is already covered by
    // the merge[Meta]Data(Read|Write)Latency metrics, so not repeated here. Can be
    // explicitly added if deemed required.

    explicit MergeHandlerMetrics(metrics::MetricSet* owner);
    ~MergeHandlerMetrics();
};

}
