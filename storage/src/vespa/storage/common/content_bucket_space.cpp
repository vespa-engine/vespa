// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "content_bucket_space.h"

namespace storage {

ContentBucketSpace::ContentBucketSpace(document::BucketSpace bucketSpace)
    : _bucketSpace(bucketSpace),
      _bucketDatabase(),
      _lock(),
      _clusterState(),
      _distribution()
{
}

void
ContentBucketSpace::setClusterState(std::shared_ptr<const lib::ClusterState> clusterState)
{
    std::lock_guard guard(_lock);
    _clusterState = std::move(clusterState);
}

std::shared_ptr<const lib::ClusterState>
ContentBucketSpace::getClusterState() const
{
    std::lock_guard guard(_lock);
    return _clusterState;
}

void
ContentBucketSpace::setDistribution(std::shared_ptr<const lib::Distribution> distribution)
{
    std::lock_guard guard(_lock);
    _distribution = std::move(distribution);
}

std::shared_ptr<const lib::Distribution>
ContentBucketSpace::getDistribution() const
{
    std::lock_guard guard(_lock);
    return _distribution;
}

}
