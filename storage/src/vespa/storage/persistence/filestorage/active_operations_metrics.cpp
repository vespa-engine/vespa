// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "active_operations_metrics.h"

namespace storage {

ActiveOperationsMetrics::ActiveOperationsMetrics(metrics::MetricSet* parent)
    : MetricSet("active_operations", {}, "metrics for active operations at service layer", parent),
      size("size", {}, "Number of concurrent active operations", this),
      latency("latency", {}, "Latency (in ms) for active operations", this)
{
}

ActiveOperationsMetrics::~ActiveOperationsMetrics() = default;

}
