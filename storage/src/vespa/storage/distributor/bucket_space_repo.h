// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucket_space.h"
#include <memory>

namespace storage {

namespace lib {
class Distribution;
}

namespace distributor {

class BucketSpaceRepo {
    // TODO: multiple spaces. This is just to start re-wiring things.
    BucketSpace _defaultSpace;
public:
    BucketSpaceRepo();
    ~BucketSpaceRepo();

    BucketSpace& getDefaultSpace() noexcept { return _defaultSpace; }
    const BucketSpace& getDefaultSpace() const noexcept {
        return _defaultSpace;
    }

    void setDefaultDistribution(std::shared_ptr<lib::Distribution> distr);
};

}
}
