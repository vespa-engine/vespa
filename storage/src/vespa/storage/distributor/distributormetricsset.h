// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "persistence_operation_metric_set.h"
#include "update_metric_set.h"
#include "visitormetricsset.h"
#include <vespa/metrics/metrics.h>
#include <vespa/metrics/common/memory_usage_metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace vespalib { class MemoryUsage; }

namespace storage::distributor {

struct BucketDbMetrics : metrics::MetricSet {
    BucketDbMetrics(const vespalib::string& db_type, metrics::MetricSet* owner);
    ~BucketDbMetrics() override;

    metrics::MemoryUsageMetrics memory_usage;
};

class DistributorMetricSet : public metrics::MetricSet {
public:
    metrics::LoadMetric<PersistenceOperationMetricSet> puts;
    metrics::LoadMetric<UpdateMetricSet> updates;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_puts;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_metadata_gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> removes;
    metrics::LoadMetric<PersistenceOperationMetricSet> removelocations;
    metrics::LoadMetric<PersistenceOperationMetricSet> gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> stats;
    metrics::LoadMetric<PersistenceOperationMetricSet> getbucketlists;
    metrics::LoadMetric<VisitorMetricSet> visits;
    metrics::DoubleAverageMetric stateTransitionTime;
    metrics::DoubleAverageMetric set_cluster_state_processing_time;
    metrics::DoubleAverageMetric activate_cluster_state_processing_time;
    metrics::DoubleAverageMetric recoveryModeTime;
    metrics::LongValueMetric docsStored;
    metrics::LongValueMetric bytesStored;
    BucketDbMetrics mutable_dbs;
    BucketDbMetrics read_only_dbs;

    explicit DistributorMetricSet(const metrics::LoadTypeSet& lt);
    ~DistributorMetricSet() override;
};

}

