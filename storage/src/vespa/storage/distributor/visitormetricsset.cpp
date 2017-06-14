// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitormetricsset.h"
#include <vespa/metrics/loadmetric.hpp>
#include <vespa/metrics/summetric.hpp>

namespace storage {

using metrics::MetricSet;

VisitorMetricSet::VisitorMetricSet(MetricSet* owner)
    : PersistenceOperationMetricSet("visitor", owner),
      buckets_per_visitor("buckets_per_visitor", "",
                          "The number of sub buckets visited as part of a "
                          "single client visitor command", this),
      docs_per_visitor("docs_per_visitor", "",
                       "The number of documents visited on content nodes as "
                       "part of a single client visitor command", this),
      bytes_per_visitor("bytes_per_visitor", "",
                        "The number of bytes visited on content nodes as part "
                        "of a single client visitor command", this)
{
}

VisitorMetricSet::~VisitorMetricSet() { }

MetricSet *
VisitorMetricSet::clone(std::vector<Metric::UP>& ownerList, CopyType copyType,
                        MetricSet* owner, bool includeUnused) const
{
    if (copyType == INACTIVE) {
        return MetricSet::clone(ownerList, INACTIVE, owner, includeUnused);
    }
    return (VisitorMetricSet*) (new VisitorMetricSet(owner))->assignValues(*this);
}

}

template class metrics::LoadMetric<storage::VisitorMetricSet>;
template class metrics::SumMetric<storage::VisitorMetricSet>;
