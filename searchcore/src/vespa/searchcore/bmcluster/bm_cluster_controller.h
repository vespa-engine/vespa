// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::bmcluster {

class BmCluster;
class IBmDistribution;

/*
 * Fake cluster controller that sets cluster state to be up.
 */
class BmClusterController
{
    const BmCluster&                  _cluster;
    const IBmDistribution&            _distribution;
public:
    BmClusterController(BmCluster& cluster, const IBmDistribution& distribution);
    void propagate_cluster_state(uint32_t node_idx, bool distributor);
    void propagate_cluster_state(bool distributor);
    void propagate_cluster_state();
};

}
