// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucket_ownership_calculator.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::distributor {

namespace {

uint64_t superbucket_from_id(const document::BucketId& id, uint16_t distribution_bits) noexcept {
    // The n LSBs of the bucket ID contain the superbucket number. Mask off the rest.
    return id.getRawId() & ~(UINT64_MAX << distribution_bits);
}

}

bool
BucketOwnershipCalculator::this_distributor_owns_bucket(const document::BucketId& bucket_id) const
{
    // TODO "no distributors available" case is the same for _all_ buckets; cache once in constructor.
    // TODO "too few bits used" case can be cheaply checked without needing exception
    try {
        const auto bits = _state.getDistributionBitCount();
        const auto this_superbucket = superbucket_from_id(bucket_id, bits);
        if (_cached_decision_superbucket == this_superbucket) {
            return _cached_owned;
        }
        uint16_t distributor = _distribution.getIdealDistributorNode(_state, bucket_id, "uim");
        _cached_decision_superbucket = this_superbucket;
        _cached_owned = (distributor == _this_node_index);
        return _cached_owned;
    } catch (lib::TooFewBucketBitsInUseException&) {
        // Ignore; implicitly not owned
    } catch (lib::NoDistributorsAvailableException&) {
        // Ignore; implicitly not owned
    }
    return false;
}

}
