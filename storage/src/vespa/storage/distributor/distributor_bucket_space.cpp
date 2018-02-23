// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space.h"
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>

namespace storage::distributor {

DistributorBucketSpace::DistributorBucketSpace()
    : _bucketDatabase(),
      _clusterState(),
      _distribution()
{
}

DistributorBucketSpace::~DistributorBucketSpace() = default;

void
DistributorBucketSpace::setClusterState(std::shared_ptr<const lib::ClusterState> clusterState)
{
    _clusterState = std::move(clusterState);
}


void
DistributorBucketSpace::setDistribution(std::shared_ptr<const lib::Distribution> distribution) {
    _distribution = std::move(distribution);
}

}
