// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distributorcomponent.h"
#include "managed_bucket_space.h"

namespace storage {
namespace distributor {

/**
 * Component bound to a specific bucket space, with utility operations to
 * operate on buckets in this space.
 */
class ManagedBucketSpaceComponent : public DistributorComponent {
    ManagedBucketSpace& _bucketSpace;
public:
    ManagedBucketSpaceComponent(DistributorInterface& distributor,
                                ManagedBucketSpace& bucketSpace,
                                DistributorComponentRegister& compReg,
                                const std::string& name);

    BucketDatabase& getBucketDatabase() override {
        return _bucketSpace.getBucketDatabase();
    }

    const BucketDatabase& getBucketDatabase() const override {
        return _bucketSpace.getBucketDatabase();
    }

    const lib::Distribution& getDistribution() const override {
        return _bucketSpace.getDistribution();
    }

};

}
}
