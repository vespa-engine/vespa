// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <memory>

namespace storage::lib {
    class ClusterState;
    class Distribution;
}

namespace storage::distributor {

/**
 * A distributor bucket space holds specific state and information required for
 * keeping track of, and computing operations for, a single bucket space:
 *
 * Bucket database instance
 *   Each bucket space has its own entirely separate bucket database.
 * Distribution config
 *   Each bucket space _may_ operate with its own distribution config, in
 *   particular so that redundancy, ready copies etc can differ across
 *   bucket spaces.
 */
class DistributorBucketSpace {
    MapBucketDatabase _bucketDatabase;
    std::shared_ptr<const lib::ClusterState> _clusterState;
    std::shared_ptr<const lib::Distribution> _distribution;
public:
    DistributorBucketSpace();
    ~DistributorBucketSpace();

    DistributorBucketSpace(const DistributorBucketSpace&) = delete;
    DistributorBucketSpace& operator=(const DistributorBucketSpace&) = delete;
    DistributorBucketSpace(DistributorBucketSpace&&) = delete;
    DistributorBucketSpace& operator=(DistributorBucketSpace&&) = delete;

    BucketDatabase& getBucketDatabase() noexcept {
        return _bucketDatabase;
    }
    const BucketDatabase& getBucketDatabase() const noexcept {
        return _bucketDatabase;
    }

    void setClusterState(std::shared_ptr<const lib::ClusterState> clusterState);

    const lib::ClusterState &getClusterState() const noexcept { return *_clusterState; }

    void setDistribution(std::shared_ptr<const lib::Distribution> distribution);

    // Precondition: setDistribution has been called at least once prior.
    const lib::Distribution& getDistribution() const noexcept {
        return *_distribution;
    }

};

}
