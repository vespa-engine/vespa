// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <memory>

namespace storage {

namespace lib {
class Distribution;
}

namespace distributor {

/**
 * A managed bucket space holds specific state and information required for
 * keeping track of, and computing operations for, a single bucket space:
 *
 * Bucket database instance
 *   Each bucket space has its own entirely separate bucket database.
 * Distribution config
 *   Each bucket space _may_ operate with its own distribution config, in
 *   particular so that redundancy, ready copies etc can differ across
 *   bucket spaces.
 */
class ManagedBucketSpace {
    MapBucketDatabase _bucketDatabase;
    std::shared_ptr<lib::Distribution> _distribution;
public:
    ManagedBucketSpace();
    ~ManagedBucketSpace();

    ManagedBucketSpace(const ManagedBucketSpace&) = delete;
    ManagedBucketSpace& operator=(const ManagedBucketSpace&) = delete;
    ManagedBucketSpace(ManagedBucketSpace&&) = delete;
    ManagedBucketSpace& operator=(ManagedBucketSpace&&) = delete;

    BucketDatabase& getBucketDatabase() noexcept {
        return _bucketDatabase;
    }
    const BucketDatabase& getBucketDatabase() const noexcept {
        return _bucketDatabase;
    }

    void setDistribution(lib::Distribution::SP distribution) {
        _distribution = std::move(distribution);
    }

    // Precondition: setDistribution has been called at least once prior.
    const lib::Distribution& getDistribution() const noexcept {
        return *_distribution;
    }

};

}
}
