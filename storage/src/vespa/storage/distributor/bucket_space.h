// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <memory>

namespace storage {
namespace distributor {

/**
 * A managed bucket space holds specific state and information required for a
 * keeping track of and computing operations for a single bucket space:
 *  - Bucket database instance
 *  - Distribution config
 *  - Cluster state
 */
class BucketSpace {
    MapBucketDatabase _bucketDatabase;
    lib::Distribution::SP _distribution;
public:
    BucketSpace();
    ~BucketSpace();

    MapBucketDatabase& getBucketDatabase() noexcept {
        return _bucketDatabase;
    }
    const MapBucketDatabase& getBucketDatabase() const noexcept {
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
