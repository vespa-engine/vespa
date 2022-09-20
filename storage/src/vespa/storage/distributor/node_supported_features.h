// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace storage::distributor {

/**
 * Collection of distinct features supported by a particular content node.
 *
 * Communicated to a distributor via bucket info exchanges. All features
 * are initially expected to be unsupported.
 */
struct NodeSupportedFeatures {
    bool unordered_merge_chaining  = false;
    bool two_phase_remove_location = false;
    bool no_implicit_indexing_of_active_buckets = false;

    bool operator==(const NodeSupportedFeatures& rhs) const noexcept = default;
};

}
