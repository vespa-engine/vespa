// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/persistence/spi/fixed_bucket_spaces.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.distributor_bucket_space_repo");

using document::BucketSpace;

namespace storage {
namespace distributor {

DistributorBucketSpaceRepo::DistributorBucketSpaceRepo(bool enableGlobalBucketSpace)
    : _map()
{
    add(spi::FixedBucketSpaces::default_space(), std::make_unique<DistributorBucketSpace>());
    if (enableGlobalBucketSpace) {
        add(spi::FixedBucketSpaces::global_space(), std::make_unique<DistributorBucketSpace>());
    }
}

DistributorBucketSpaceRepo::~DistributorBucketSpaceRepo() = default;

void
DistributorBucketSpaceRepo::add(document::BucketSpace bucketSpace, std::unique_ptr<DistributorBucketSpace> distributorBucketSpace)
{
    _map.emplace(bucketSpace, std::move(distributorBucketSpace));
}

DistributorBucketSpace &
DistributorBucketSpaceRepo::get(BucketSpace bucketSpace)
{
    auto itr = _map.find(bucketSpace);
    assert(itr != _map.end());
    return *itr->second;
}

const DistributorBucketSpace &
DistributorBucketSpaceRepo::get(BucketSpace bucketSpace) const
{
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
