// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_space_repo.h"
#include <vespa/vdslib/distribution/distribution.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.bucket_space_repo");

namespace storage {
namespace distributor {

BucketSpaceRepo::BucketSpaceRepo() {
}

BucketSpaceRepo::~BucketSpaceRepo() {
}

void BucketSpaceRepo::setDefaultDistribution(
        std::shared_ptr<lib::Distribution> distr)
{
    LOG(debug, "Got new distribution '%s'", distr->toString().c_str());
    // TODO all spaces, per-space config transforms
    _defaultSpace.setDistribution(std::move(distr));
}

}
}
