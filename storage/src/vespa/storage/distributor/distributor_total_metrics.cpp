// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_total_metrics.h"

namespace storage::distributor {

DistributorTotalMetrics::DistributorTotalMetrics(uint32_t num_distributor_stripes)
    : DistributorMetricSet(),
      _stripes_metrics(),
      _bucket_db_updater_metrics()
{
    _stripes_metrics.reserve(num_distributor_stripes);
    for (uint32_t i = 0; i < num_distributor_stripes; ++i) {
        _stripes_metrics.emplace_back(std::make_shared<DistributorMetricSet>());
    }
}

DistributorTotalMetrics::~DistributorTotalMetrics() = default;

void
DistributorTotalMetrics::aggregate_helper(DistributorMetricSet &total) const
{
    _bucket_db_updater_metrics.addToPart(total);
    for (auto &stripe_metrics : _stripes_metrics) {
        stripe_metrics->addToPart(total);
    }
}

void
DistributorTotalMetrics::aggregate()
{
    DistributorMetricSet::reset();
    aggregate_helper(*this);
}

void
DistributorTotalMetrics::addToSnapshot(Metric& m, std::vector<Metric::UP> &ownerList) const
{
    DistributorMetricSet total;
    aggregate_helper(total);
    total.addToSnapshot(m, ownerList);
}

void
DistributorTotalMetrics::reset()
{
    DistributorMetricSet::reset();
    _bucket_db_updater_metrics.reset();
    for (auto &stripe_metrics : _stripes_metrics) {
        stripe_metrics->reset();
    }
}

}
