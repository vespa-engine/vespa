// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ideal_state_total_metrics.h"

namespace storage::distributor {

void
IdealStateTotalMetrics::aggregate_helper(IdealStateMetricSet& total) const
{
    for (auto& stripe_metrics : _stripes_metrics) {
        stripe_metrics->addToPart(total);
    }
}

IdealStateTotalMetrics::IdealStateTotalMetrics(uint32_t num_distributor_stripes)
    : IdealStateMetricSet(),
      _stripes_metrics()
{
    _stripes_metrics.reserve(num_distributor_stripes);
    for (uint32_t i = 0; i < num_distributor_stripes; ++i) {
        _stripes_metrics.emplace_back(std::make_shared<IdealStateMetricSet>());
    }
}

IdealStateTotalMetrics::~IdealStateTotalMetrics() = default;

void
IdealStateTotalMetrics::aggregate()
{
    IdealStateMetricSet::reset();
    aggregate_helper(*this);
}

void
IdealStateTotalMetrics::addToSnapshot(Metric& m, std::vector<Metric::UP>& owner_list) const
{
    IdealStateMetricSet total;
    aggregate_helper(total);
    total.addToSnapshot(m, owner_list);
}

void
IdealStateTotalMetrics::reset()
{
    IdealStateMetricSet::reset();
    for (auto& stripe_metrics : _stripes_metrics) {
        stripe_metrics->reset();
    }
}

}
