// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "persistence_operation_metric_set.h"

namespace storage::distributor {

struct VisitorMetricSet : public PersistenceOperationMetricSet {
    metrics::LongAverageMetric buckets_per_visitor;
    metrics::LongAverageMetric docs_per_visitor;
    metrics::LongAverageMetric bytes_per_visitor;

    VisitorMetricSet(MetricSet* owner = nullptr);
    ~VisitorMetricSet() override;

    MetricSet * clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                      MetricSet* owner, bool includeUnused) const override;
};

} // storage::distributor
