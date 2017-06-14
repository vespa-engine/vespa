// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/storage/distributor/operations/idealstate/idealstateoperation.h>

namespace storage {

namespace distributor {

class OperationMetricSet : public metrics::MetricSet
{
public:
    metrics::LongValueMetric pending;
    metrics::LongCountMetric ok;
    metrics::LongCountMetric failed;

    OperationMetricSet(const std::string& name, const std::string& tags, const std::string& description, MetricSet* owner);
    ~OperationMetricSet();
};

class IdealStateMetricSet : public metrics::MetricSet
{
public:
    std::vector<std::shared_ptr<OperationMetricSet> > operations;
    metrics::LongValueMetric idealstate_diff;
    metrics::LongValueMetric buckets_toofewcopies;
    metrics::LongValueMetric buckets_toomanycopies;
    metrics::LongValueMetric buckets;
    metrics::LongValueMetric buckets_notrusted;
    metrics::LongValueMetric buckets_rechecking;
    metrics::LongAverageMetric startOperationsLatency;
    metrics::DoubleAverageMetric nodesPerMerge;

    void createOperationMetrics();

    IdealStateMetricSet();
    ~IdealStateMetricSet();

    void setPendingOperations(const std::vector<uint64_t>& newMetrics);
};

}

}

