// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.distributor_bucket_space_repo");

using document::BucketSpace;

namespace storage {
namespace distributor {

DistributorBucketSpaceRepo::DistributorBucketSpaceRepo()
    : _map()
{
    _map.emplace(BucketSpace::placeHolder(), std::make_unique<DistributorBucketSpace>());
}

DistributorBucketSpaceRepo::~DistributorBucketSpaceRepo() {
}

void DistributorBucketSpaceRepo::setDefaultDistribution(
        std::shared_ptr<lib::Distribution> distr)
{
    LOG(debug, "Got new default distribution '%s'", distr->toString().c_str());
    // TODO all spaces, per-space config transforms
    getDefaultSpace().setDistribution(std::move(distr));
}

DistributorBucketSpace &
DistributorBucketSpaceRepo::get(BucketSpace bucketSpace)
{
    assert(bucketSpace == BucketSpace::placeHolder());
    auto itr = _map.find(bucketSpace);
    assert(itr != _map.end());
    return *itr->second;
}

const DistributorBucketSpace &
DistributorBucketSpaceRepo::get(BucketSpace bucketSpace) const
{
    assert(bucketSpace == BucketSpace::placeHolder());
    auto itr = _map.find(bucketSpace);
    assert(itr != _map.end());
    return *itr->second;
}

DistributorBucketSpace &
DistributorBucketSpaceRepo::getDefaultSpace() noexcept
{
    return get(BucketSpace::placeHolder());
}

const DistributorBucketSpace &
DistributorBucketSpaceRepo::getDefaultSpace() const noexcept
{
    return get(BucketSpace::placeHolder());
}

}
}
