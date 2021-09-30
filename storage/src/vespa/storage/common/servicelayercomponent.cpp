// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayercomponent.h"

#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/vdslib/distribution/distribution.h>

using document::BucketSpace;

namespace storage {

const ContentBucketSpaceRepo &
ServiceLayerComponent::getBucketSpaceRepo() const
{
    assert(_bucketSpaceRepo != nullptr);
    return *_bucketSpaceRepo;
}

StorBucketDatabase&
ServiceLayerComponent::getBucketDatabase(BucketSpace bucketSpace) const
{
    assert(_bucketSpaceRepo != nullptr);
    return _bucketSpaceRepo->get(bucketSpace).bucketDatabase();
}

} // storage
