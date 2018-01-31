// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {

namespace api {
class ReturnCode;
}

class PersistenceFailuresMetricSet : public metrics::MetricSet
{
public:
    explicit PersistenceFailuresMetricSet(metrics::MetricSet* owner);
    ~PersistenceFailuresMetricSet();

    metrics::SumMetric<metrics::LongCountMetric> sum;
    metrics::LongCountMetric notready;
    metrics::LongCountMetric notconnected;
    metrics::LongCountMetric wrongdistributor;
    metrics::LongCountMetric safe_time_not_reached;
    metrics::LongCountMetric storagefailure;
    metrics::LongCountMetric timeout;
    metrics::LongCountMetric busy;
    metrics::LongCountMetric inconsistent_bucket;
    metrics::LongCountMetric notfound;
    metrics::LongCountMetric concurrent_mutations;

    MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                      metrics::MetricSet* owner, bool includeUnused) const override;
};

class PersistenceOperationMetricSet : public metrics::MetricSet
{
public:
    metrics::DoubleAverageMetric latency;
    metrics::LongCountMetric ok;
    PersistenceFailuresMetricSet failures;

    PersistenceOperationMetricSet(const std::string& name, metrics::MetricSet* owner = nullptr);
    ~PersistenceOperationMetricSet();

    MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                      metrics::MetricSet* owner, bool includeUnused) const override;

    /**
     * Increments appropriate success/failure count metrics based on the return
     * code provided in `result`.
     *
     * Does _not_ update latency metric.
     */
    void updateFromResult(const api::ReturnCode& result);
};

}
