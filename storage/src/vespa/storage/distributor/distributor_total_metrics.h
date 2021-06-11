// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distributormetricsset.h"

namespace storage::distributor {

/*
 * Class presenting total metrics (as a DistributorMetricSet) to the
 * metric framework, while managing a DistributorMetricSet for each
 * stripe and an extra one for the top level bucket db updater.
 */
class DistributorTotalMetrics : public DistributorMetricSet
{
    std::vector<std::shared_ptr<DistributorMetricSet>> _stripes_metrics;
    DistributorMetricSet _bucket_db_updater_metrics;
public:
    explicit DistributorTotalMetrics(uint32_t num_distributor_stripes);
    ~DistributorTotalMetrics() override;
    void aggregate();
    void reset() override;
    DistributorMetricSet& stripe(uint32_t stripe_index) { return *_stripes_metrics[stripe_index]; }
    DistributorMetricSet& bucket_db_updater_metrics() { return _bucket_db_updater_metrics; }
};

}
