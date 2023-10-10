// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace document { class BucketId; }

namespace storage::lib {
class ClusterState;
class Distribution;
}

namespace storage::distributor {

/**
 * Calculator for determining if a bucket is owned by the current distributor.
 * Ideal state calculations are cached and reused for all consecutive sub-buckets
 * under the same super bucket. The cache is invalidated when a new super bucket
 * is encountered, so it only provides a benefit when invoked in bucket ID order.
 *
 * Not thread safe due to internal caching.
 */
class BucketOwnershipCalculator {
    const lib::ClusterState& _state;
    const lib::Distribution& _distribution;
    mutable uint64_t         _cached_decision_superbucket;
    const uint16_t           _this_node_index;
    mutable bool             _cached_owned;
public:
    BucketOwnershipCalculator(const lib::ClusterState& state,
                              const lib::Distribution& distribution,
                              uint16_t this_node_index) noexcept
        : _state(state),
          _distribution(distribution),
          _cached_decision_superbucket(UINT64_MAX),
          _this_node_index(this_node_index),
          _cached_owned(false)
    {
    }

    [[nodiscard]] bool this_distributor_owns_bucket(const document::BucketId& bucket_id) const;
};

}
