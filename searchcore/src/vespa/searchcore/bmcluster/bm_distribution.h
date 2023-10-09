// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bm_distribution.h"
#include <vespa/config-stor-distribution.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>

namespace search::bmcluster {

/*
 * Class for describing cluster toplogy and how messages are
 * routed from feeders into the cluster.
 */
class BmDistribution : public IBmDistribution
{
    uint32_t                         _num_nodes;
    DistributionConfigBuilder        _distribution_config;
    storage::lib::Distribution       _distribution;
    storage::lib::ClusterState       _pending_cluster_state;
    storage::lib::ClusterStateBundle _cluster_state_bundle;
    bool                             _has_pending_cluster_state;
public:
    BmDistribution(uint32_t groups, uint32_t nodes_per_group, uint32_t redundancy);
    ~BmDistribution() override;
    uint32_t get_num_nodes() const override;
    uint32_t get_service_layer_node_idx(const document::Bucket & bucket) const override;
    uint32_t get_distributor_node_idx(const document::Bucket & bucket) const override;
    DistributionConfig get_distribution_config() const override;
    storage::lib::ClusterStateBundle get_cluster_state_bundle() const override;
    void set_node_state(uint32_t node_idx, bool distributor, const storage::lib::State& state);
    void set_node_state(uint32_t node_idx, const storage::lib::State& state);
    void commit_cluster_state_change();
};

};
