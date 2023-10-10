// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idealstatemetricsset.h"

namespace storage::distributor {

/*
 * Class presenting total metrics (as an IdealStateMetricSet) to the metric framework,
 * while managing an IdealStateMetricSet for each distributor stripe.
 */
class IdealStateTotalMetrics : public IdealStateMetricSet {
private:
    std::vector<std::shared_ptr<IdealStateMetricSet>> _stripes_metrics;

    void aggregate_helper(IdealStateMetricSet& total) const;

public:
    explicit IdealStateTotalMetrics(uint32_t num_distributor_stripes);
    ~IdealStateTotalMetrics() override;
    void aggregate();
    void addToSnapshot(Metric& m, std::vector<Metric::UP>& owner_list) const override;
    void reset() override;
    IdealStateMetricSet& stripe(uint32_t stripe_index) { return *_stripes_metrics[stripe_index]; }
};

}
