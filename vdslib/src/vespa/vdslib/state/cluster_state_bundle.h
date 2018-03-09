// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <unordered_map>
#include <iosfwd>

namespace storage::lib {

class ClusterState;

/**
 * Class representing the baseline cluster state and the derived cluster
 * state for each bucket space.
 */
class ClusterStateBundle
{
public:
    using BucketSpaceStateMapping = std::unordered_map<
        document::BucketSpace,
        std::shared_ptr<const ClusterState>,
        document::BucketSpace::hash
    >;
    std::shared_ptr<const ClusterState> _baselineClusterState;
    BucketSpaceStateMapping _derivedBucketSpaceStates;
public:
    explicit ClusterStateBundle(const ClusterState &baselineClusterState);
    ClusterStateBundle(const ClusterState& baselineClusterState,
                       BucketSpaceStateMapping derivedBucketSpaceStates);
    ~ClusterStateBundle();
    const std::shared_ptr<const ClusterState> &getBaselineClusterState() const;
    const std::shared_ptr<const ClusterState> &getDerivedClusterState(document::BucketSpace bucketSpace) const;
    const BucketSpaceStateMapping& getDerivedClusterStates() const noexcept {
        return _derivedBucketSpaceStates;
    }
    uint32_t getVersion() const;
    bool operator==(const ClusterStateBundle &rhs) const;
    bool operator!=(const ClusterStateBundle &rhs) const { return !operator==(rhs); }
};

std::ostream& operator<<(std::ostream&, const ClusterStateBundle&);

}
