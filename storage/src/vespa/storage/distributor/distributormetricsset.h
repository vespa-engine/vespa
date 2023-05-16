// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "persistence_operation_metric_set.h"
#include "update_metric_set.h"
#include "visitormetricsset.h"
#include <vespa/metrics/common/memory_usage_metrics.h>

namespace vespalib { class MemoryUsage; }

namespace storage::distributor {

struct BucketDbMetrics : metrics::MetricSet {
    BucketDbMetrics(const vespalib::string& db_type, metrics::MetricSet* owner);
    ~BucketDbMetrics() override;

    metrics::MemoryUsageMetrics memory_usage;
};

class DistributorMetricSet : public metrics::MetricSet {
public:
    PersistenceOperationMetricSet puts;
    PersistenceOperationMetricSet put_condition_probes;
    UpdateMetricSet               updates;
    PersistenceOperationMetricSet update_puts;
    PersistenceOperationMetricSet update_gets;
    PersistenceOperationMetricSet update_metadata_gets;
    PersistenceOperationMetricSet removes;
    PersistenceOperationMetricSet remove_condition_probes;
    PersistenceOperationMetricSet removelocations;
    PersistenceOperationMetricSet gets;
    PersistenceOperationMetricSet stats;
    PersistenceOperationMetricSet getbucketlists;
    VisitorMetricSet              visits;
    metrics::DoubleAverageMetric  stateTransitionTime;
    metrics::DoubleAverageMetric  set_cluster_state_processing_time;
    metrics::DoubleAverageMetric  activate_cluster_state_processing_time;
    metrics::DoubleAverageMetric  recoveryModeTime;
    metrics::LongValueMetric      docsStored;
    metrics::LongValueMetric      bytesStored;
    BucketDbMetrics               mutable_dbs;
    BucketDbMetrics               read_only_dbs;

    explicit DistributorMetricSet();
    ~DistributorMetricSet() override;
};

}

