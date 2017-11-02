// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space_repo.h"
#include <vespa/vdslib/distribution/distribution.h>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.managed_bucket_space_repo");

namespace storage {
namespace distributor {

DistributorBucketSpaceRepo::DistributorBucketSpaceRepo() {
}

DistributorBucketSpaceRepo::~DistributorBucketSpaceRepo() {
}

void DistributorBucketSpaceRepo::setDefaultDistribution(
        std::shared_ptr<lib::Distribution> distr)
{
    LOG(debug, "Got new default distribution '%s'", distr->toString().c_str());
    // TODO all spaces, per-space config transforms
    _defaultSpace.setDistribution(std::move(distr));
}

}
}
