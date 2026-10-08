// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "servicelayercomponent.h"

#include <vespa/check_require.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/vdslib/distribution/distribution.h>

using document::BucketSpace;

namespace storage {

const ContentBucketSpaceRepo& ServiceLayerComponent::getBucketSpaceRepo() const {
    CHECK(_bucketSpaceRepo != nullptr);
    return *_bucketSpaceRepo;
}

StorBucketDatabase& ServiceLayerComponent::getBucketDatabase(BucketSpace bucketSpace) const {
    CHECK(_bucketSpaceRepo != nullptr);
    return _bucketSpaceRepo->get(bucketSpace).bucketDatabase();
}

} // namespace storage
