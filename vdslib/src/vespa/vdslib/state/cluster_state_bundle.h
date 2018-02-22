// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketspace.h>

namespace storage::lib {

class ClusterState;

/**
 * Class representing the baseline cluster state and the derived cluster
 * state for each bucket space.
 */
class ClusterStateBundle
{
    std::shared_ptr<const ClusterState> _baselineClusterState;
public:
    explicit ClusterStateBundle(const ClusterState &baselineClusterState);
    ~ClusterStateBundle();
    const std::shared_ptr<const ClusterState> &getBaselineClusterState() const;
    const std::shared_ptr<const ClusterState> &getDerivedClusterState(document::BucketSpace bucketSpace) const;
    uint32_t getVersion() const;
};

}
