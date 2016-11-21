// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {

struct VisitorMetricSet : public metrics::MetricSet {
    metrics::DoubleAverageMetric latency;
    metrics::LongCountMetric failed;

    VisitorMetricSet(MetricSet* owner = 0);
    ~VisitorMetricSet();

    MetricSet * clone(std::vector<Metric::LP>& ownerList, CopyType copyType,
                      MetricSet* owner, bool includeUnused) const override;

    VisitorMetricSet* operator&() { return this; }
};

} // storage

