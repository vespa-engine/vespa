// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace storage::lib {
class ClusterState;
}

namespace storage::distributor {

/*
 * Returns whether the state transition from a -> b is idempotent in terms
 * of buckets needing to be pruned from the distributor's bucket database.
 *
 * Examples of when this is the case:
 *   - `a` and `b` differ only in state version number
 *   - Storage node 1 is .s:d in `a`, and .s:m in `b`. Buckets have already
 *     been pruned when `a` was processed.
 *   - Node startup timestamps have been changed. This will trigger bucket
 *     info re-fetches if the distributor observes a higher startup timestamp
 *     than it currently was aware of, but does not need any pruning.
 */
bool db_pruning_may_be_elided(const lib::ClusterState& a,
                              const lib::ClusterState& b,
                              const char* up_states = "uri");

}
