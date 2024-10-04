// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace storage::distributor {

/**
 * Collection of distinct features supported by a particular content node.
 *
 * Communicated to a distributor via bucket info exchanges. All features
 * are initially expected to be unsupported.
 */
struct NodeSupportedFeatures {
    bool unordered_merge_chaining               = false;
    bool two_phase_remove_location              = false;
    bool no_implicit_indexing_of_active_buckets = false;
    bool document_condition_probe               = false;
    bool timestamps_in_tas_conditions           = false;

    bool operator==(const NodeSupportedFeatures& rhs) const noexcept = default;

    [[nodiscard]] NodeSupportedFeatures intersection_of(const NodeSupportedFeatures& rhs) const noexcept {
        NodeSupportedFeatures ret;
        ret.unordered_merge_chaining               = (unordered_merge_chaining && rhs.unordered_merge_chaining);
        ret.two_phase_remove_location              = (two_phase_remove_location && rhs.two_phase_remove_location);
        ret.no_implicit_indexing_of_active_buckets = (no_implicit_indexing_of_active_buckets && rhs.no_implicit_indexing_of_active_buckets);
        ret.document_condition_probe               = (document_condition_probe && rhs.document_condition_probe);
        ret.timestamps_in_tas_conditions           = (timestamps_in_tas_conditions && rhs.timestamps_in_tas_conditions);
        return ret;
    }
};

}
