// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "content_bucket_space_repo.h"
#include <vespa/persistence/spi/fixed_bucket_spaces.h>

using document::BucketSpace;

namespace storage {

ContentBucketSpaceRepo::ContentBucketSpaceRepo()
    : _map()
{
    _map.emplace(spi::FixedBucketSpaces::default_space(), std::make_unique<ContentBucketSpace>(spi::FixedBucketSpaces::default_space()));
}

ContentBucketSpace &
ContentBucketSpaceRepo::get(BucketSpace bucketSpace) const
{
    auto itr = _map.find(bucketSpace);
    assert(itr != _map.end());
    return *itr->second;
}

void ContentBucketSpaceRepo::enableGlobalBucketSpace() {
    _map.emplace(spi::FixedBucketSpaces::global_space(), std::make_unique<ContentBucketSpace>(spi::FixedBucketSpaces::global_space()));
}

ContentBucketSpaceRepo::BucketSpaces
ContentBucketSpaceRepo::getBucketSpaces() const
{
    BucketSpaces result;
    for (const auto &elem : _map) {
        result.push_back(elem.first);
    }
    return result;
}

size_t
ContentBucketSpaceRepo::getBucketMemoryUsage() const
{
    size_t result = 0;
    for (const auto &elem : _map) {
        result += elem.second->bucketDatabase().getMemoryUsage();
    }
    return result;
}

}
