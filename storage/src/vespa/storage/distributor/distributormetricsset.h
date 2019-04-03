// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "persistence_operation_metric_set.h"
#include "update_metric_set.h"
#include "visitormetricsset.h"
#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {

class DistributorMetricSet : public metrics::MetricSet
{
public:
    metrics::LoadMetric<PersistenceOperationMetricSet> puts;
    metrics::LoadMetric<UpdateMetricSet> updates;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_puts;
    metrics::LoadMetric<PersistenceOperationMetricSet> update_gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> removes;
    metrics::LoadMetric<PersistenceOperationMetricSet> removelocations;
    metrics::LoadMetric<PersistenceOperationMetricSet> gets;
    metrics::LoadMetric<PersistenceOperationMetricSet> stats;
    metrics::LoadMetric<PersistenceOperationMetricSet> getbucketlists;
    metrics::LoadMetric<VisitorMetricSet> visits;
    metrics::DoubleAverageMetric stateTransitionTime;
    metrics::DoubleAverageMetric recoveryModeTime;
    metrics::LongValueMetric docsStored;
    metrics::LongValueMetric bytesStored;

    DistributorMetricSet(const metrics::LoadTypeSet& lt);
    ~DistributorMetricSet();
};

}

