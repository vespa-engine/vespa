// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space_component.h"

namespace storage::distributor {

DistributorBucketSpaceComponent::DistributorBucketSpaceComponent(
        DistributorInterface& distributor,
        DistributorBucketSpace& bucketSpace,
        DistributorComponentRegister& compReg,
        const std::string& name)
    : DistributorComponent(distributor, compReg, name),
      _bucketSpace(bucketSpace)
{
}

}
