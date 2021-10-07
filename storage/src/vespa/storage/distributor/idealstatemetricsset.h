// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/countmetric.h>
#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>

namespace storage {

namespace distributor {

class OperationMetricSet : public metrics::MetricSet
{
public:
    metrics::LongValueMetric pending;
    metrics::LongCountMetric ok;
    metrics::LongCountMetric failed;

    OperationMetricSet(const std::string& name, metrics::Metric::Tags tags, const std::string& description, MetricSet* owner);
    ~OperationMetricSet() override;
};

struct GcMetricSet : OperationMetricSet {
    metrics::LongCountMetric documents_removed;

    GcMetricSet(const std::string& name, metrics::Metric::Tags tags,
                const std::string& description, MetricSet* owner);
    ~GcMetricSet() override;
};

class IdealStateMetricSet : public metrics::MetricSet
{
public:
    std::vector<std::shared_ptr<OperationMetricSet>> operations;
    metrics::LongValueMetric idealstate_diff;
    metrics::LongValueMetric buckets_toofewcopies;
    metrics::LongValueMetric buckets_toomanycopies;
    metrics::LongValueMetric buckets;
    metrics::LongValueMetric buckets_notrusted;
    metrics::LongValueMetric buckets_rechecking; // TODO remove, not used (but exposed by VespaMetricSet)
    metrics::LongValueMetric buckets_replicas_moving_out;
    metrics::LongValueMetric buckets_replicas_copying_in;
    metrics::LongValueMetric buckets_replicas_copying_out;
    metrics::LongValueMetric buckets_replicas_syncing;
    metrics::DoubleAverageMetric nodesPerMerge;

    void createOperationMetrics();

    IdealStateMetricSet();
    ~IdealStateMetricSet() override;

    void setPendingOperations(const std::vector<uint64_t>& newMetrics);
};

}

}

