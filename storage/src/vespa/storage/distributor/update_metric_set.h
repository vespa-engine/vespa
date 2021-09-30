// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "persistence_operation_metric_set.h"

namespace storage {

class UpdateMetricSet : public PersistenceOperationMetricSet {
public:
    metrics::LongCountMetric diverging_timestamp_updates;
    metrics::LongCountMetric fast_path_restarts;

    explicit UpdateMetricSet(MetricSet* owner = nullptr);
    ~UpdateMetricSet() override;

    MetricSet* clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                     MetricSet* owner, bool includeUnused) const override;
};

} // storage


