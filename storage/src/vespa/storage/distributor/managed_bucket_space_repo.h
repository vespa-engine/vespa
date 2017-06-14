// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "managed_bucket_space.h"
#include <memory>

namespace storage {

namespace distributor {

class ManagedBucketSpaceRepo {
    // TODO: multiple spaces. This is just to start re-wiring things.
    ManagedBucketSpace _defaultSpace;
public:
    ManagedBucketSpaceRepo();
    ~ManagedBucketSpaceRepo();

    ManagedBucketSpaceRepo(const ManagedBucketSpaceRepo&&) = delete;
    ManagedBucketSpaceRepo& operator=(const ManagedBucketSpaceRepo&) = delete;
    ManagedBucketSpaceRepo(ManagedBucketSpaceRepo&&) = delete;
    ManagedBucketSpaceRepo& operator=(ManagedBucketSpaceRepo&&) = delete;

    ManagedBucketSpace& getDefaultSpace() noexcept { return _defaultSpace; }
    const ManagedBucketSpace& getDefaultSpace() const noexcept {
        return _defaultSpace;
    }

    void setDefaultDistribution(std::shared_ptr<lib::Distribution> distr);
};

}
}
