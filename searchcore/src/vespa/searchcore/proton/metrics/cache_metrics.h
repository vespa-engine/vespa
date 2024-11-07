// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>
#include <string>

namespace vespalib { struct CacheStats; }

namespace proton {

class CacheMetrics : public metrics::MetricSet {
    metrics::LongValueMetric memoryUsage;
    metrics::LongValueMetric elements;
    metrics::LongAverageMetric hitRate;
    metrics::LongCountMetric lookups;
    metrics::LongCountMetric invalidations;
    std::string _cache_name;

    void update_hit_rate(const vespalib::CacheStats &current, const vespalib::CacheStats &last);
    static void update_count_metric(uint64_t currVal, uint64_t lastVal, metrics::LongCountMetric &metric);
public:
    CacheMetrics(metrics::MetricSet* parent, const std::string& name, const std::string& description,
                 const std::string& cache_name);
    ~CacheMetrics() override;
    void update_metrics(const vespalib::CacheStats& current, const vespalib::CacheStats& last);
};

}
