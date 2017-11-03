// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributor_bucket_space.h"
#include <vespa/document/bucket/bucketspace.h>
#include <memory>

namespace storage {

namespace distributor {

class DistributorBucketSpaceRepo {
    // TODO: multiple spaces. This is just to start re-wiring things.
    DistributorBucketSpace _defaultSpace;
public:
    DistributorBucketSpaceRepo();
    ~DistributorBucketSpaceRepo();

    DistributorBucketSpaceRepo(const DistributorBucketSpaceRepo&&) = delete;
    DistributorBucketSpaceRepo& operator=(const DistributorBucketSpaceRepo&) = delete;
    DistributorBucketSpaceRepo(DistributorBucketSpaceRepo&&) = delete;
    DistributorBucketSpaceRepo& operator=(DistributorBucketSpaceRepo&&) = delete;

    DistributorBucketSpace& getDefaultSpace() noexcept { return _defaultSpace; }
    const DistributorBucketSpace& getDefaultSpace() const noexcept {
        return _defaultSpace;
    }
    DistributorBucketSpace &get(document::BucketSpace bucketSpace);
    const DistributorBucketSpace &get(document::BucketSpace bucketSpace) const;

    void setDefaultDistribution(std::shared_ptr<lib::Distribution> distr);
};

}
}
