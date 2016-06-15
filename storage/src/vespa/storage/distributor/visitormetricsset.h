// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>

namespace storage {

struct VisitorMetricSet : public metrics::MetricSet {
    metrics::DoubleAverageMetric latency;
    metrics::LongCountMetric failed;

    VisitorMetricSet(metrics::MetricSet* owner = 0)
        : metrics::MetricSet("visitor", "visitor", "", owner),
          latency("latency", "", "Latency of visitor (in ms)", this),
          failed("failed", "", "Number of visitors that failed or were aborted by the user", this)
    {
    }

    virtual Metric* clone(std::vector<Metric::LP>& ownerList,
                          CopyType copyType,
                          metrics::MetricSet* owner,
                          bool includeUnused) const
    {
        if (copyType == INACTIVE) {
            return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
        }
        return (VisitorMetricSet*)
                (new VisitorMetricSet(owner))->assignValues(*this);
    }

    VisitorMetricSet* operator&() { return this; }
};

} // storage

