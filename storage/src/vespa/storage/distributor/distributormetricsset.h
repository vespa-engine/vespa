// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitormetricsset.h"
#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {

namespace api {
class ReturnCode;
}

class PersistenceFailuresMetricSet : public metrics::MetricSet
{
public:
    PersistenceFailuresMetricSet(metrics::MetricSet* owner);
    ~PersistenceFailuresMetricSet();

    metrics::SumMetric<metrics::LongCountMetric> sum;
    metrics::LongCountMetric notready;
    metrics::LongCountMetric notconnected;
    metrics::LongCountMetric wrongdistributor;
    metrics::LongCountMetric safe_time_not_reached;
    metrics::LongCountMetric storagefailure;
    metrics::LongCountMetric timeout;
    metrics::LongCountMetric busy;
    metrics::LongCountMetric notfound;

    MetricSet * clone(std::vector<Metric::LP>& ownerList, CopyType copyType,
                      metrics::MetricSet* owner, bool includeUnused) const;
};

class PersistenceOperationMetricSet : public metrics::MetricSet
{
public:
    metrics::DoubleAverageMetric latency;
    metrics::LongCountMetric ok;
    PersistenceFailuresMetricSet failures;

    PersistenceOperationMetricSet(const std::string& name, metrics::MetricSet* owner = nullptr);
    ~PersistenceOperationMetricSet();

    MetricSet * clone(std::vector<Metric::LP>& ownerList, CopyType copyType,
                      metrics::MetricSet* owner, bool includeUnused) const override;

    /**
     * Increments appropriate success/failure count metrics based on the return
     * code provided in `result`.
     *
     * Does _not_ update latency metric.
     */
    void updateFromResult(const api::ReturnCode& result);
};

class DistributorMetricSet : public metrics::MetricSet
{
public:
    metrics::LoadMetric<PersistenceOperationMetricSet> puts;
    metrics::LoadMetric<PersistenceOperationMetricSet> updates;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_puts;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> removes;
    metrics::LoadMetric<PersistenceOperationMetricSet> removelocations;
    metrics::LoadMetric<PersistenceOperationMetricSet> gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> stats;
    metrics::LoadMetric<PersistenceOperationMetricSet> multioperations;
    metrics::LoadMetric<VisitorMetricSet> visits;
    metrics::DoubleAverageMetric recoveryModeTime;
    metrics::LongValueMetric docsStored;
    metrics::LongValueMetric bytesStored;

    DistributorMetricSet(const metrics::LoadTypeSet& lt);
    ~DistributorMetricSet();
};

}

