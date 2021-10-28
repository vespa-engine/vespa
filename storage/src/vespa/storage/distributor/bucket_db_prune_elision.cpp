// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_db_prune_elision.h"
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::distributor {

// Returns whether the set of nodes of type `node_type` across two cluster states
// are idempotent from the perspective of bucket pruning. This is the case iff
// the effective down/up state of each node is unchanged. I.e. if the only difference
// between two states is that a storage node goes from state Down to Maintenance (or
// vice versa), the buckets shall already have been pruned during processing of the
// first edge, so the subsequent pruning may be elided.
//
// If a node's state is not one of `up_states`, it is considered to be in an effective
// Down state, and vice versa.
//
// Precondition: a.getNodeCount(node_type) == b.getNodeCount(node_type)
bool node_states_are_idempotent_for_pruning(const lib::NodeType& node_type,
                                            const lib::ClusterState& a,
                                            const lib::ClusterState& b,
                                            const char* up_states)
{
    const uint16_t node_count = a.getNodeCount(node_type);
    for (uint16_t i = 0; i < node_count; ++i) {
        lib::Node node(node_type, i);
        const auto& a_s = a.getNodeState(node);
        const auto& b_s = b.getNodeState(node);
        // Transitioning from one effective Down state to another can elide DB pruning,
        // as the DB shall already have been pruned of all relevant buckets on the _first_
        // effective Down edge.
        // No pruning shall take place on a transition from one effective Up state to another
        // effective Up state.
        if (a_s.getState().oneOf(up_states) != b_s.getState().oneOf(up_states)) {
            return false;
        }
    }
    return true;
}

bool db_pruning_may_be_elided(const lib::ClusterState& a, const lib::ClusterState& b, const char* up_states) {
    if (a.getClusterState() != b.getClusterState()) {
        return false;
    }
    if (a.getDistributionBitCount() != b.getDistributionBitCount()) {
        return false;
    }
    if (a.getNodeCount(lib::NodeType::DISTRIBUTOR) != b.getNodeCount(lib::NodeType::DISTRIBUTOR)) {
        return false;
    }
    if (a.getNodeCount(lib::NodeType::STORAGE) != b.getNodeCount(lib::NodeType::STORAGE)) {
        return false;
    }
    return (node_states_are_idempotent_for_pruning(lib::NodeType::DISTRIBUTOR, a, b, up_states) &&
            node_states_are_idempotent_for_pruning(lib::NodeType::STORAGE, a, b, up_states));
}

}
