// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "memory_usage_metrics.h"

namespace proton {

/*
 * Class containing metrics for an aspect (attribute or index) of a field.
 */
struct FieldMetricsEntry : public metrics::MetricSet {
    MemoryUsageMetrics        memoryUsage;
    metrics::LongValueMetric  size_on_disk;
    FieldMetricsEntry(const std::string& name, const std::string& field_name, const std::string& description);
    ~FieldMetricsEntry() override;
};

} // namespace proton
