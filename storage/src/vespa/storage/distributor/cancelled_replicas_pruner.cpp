// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "cancelled_replicas_pruner.h"

namespace storage::distributor {

std::vector<BucketCopy> prune_cancelled_nodes(std::span<const BucketCopy> replicas, const CancelScope& cancel_scope) {
    if (cancel_scope.fully_cancelled()) {
        return {};
    }
    std::vector<BucketCopy> pruned_replicas;
    // Expect that there will be an input entry for each cancelled node in the common case.
    pruned_replicas.reserve((replicas.size() >= cancel_scope.cancelled_nodes().size())
                            ? replicas.size() - cancel_scope.cancelled_nodes().size() : 0);
    for (auto& candidate : replicas) {
        if (!cancel_scope.node_is_cancelled(candidate.getNode())) {
            pruned_replicas.emplace_back(candidate);
        }
    }
    return pruned_replicas;
}

}
