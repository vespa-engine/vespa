// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucket_space_distribution_configs.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/vdslib/distribution/distribution.h>

namespace storage::distributor {

BucketSpaceDistributionConfigs
BucketSpaceDistributionConfigs::from_default_distribution(std::shared_ptr<const lib::Distribution> distribution) {
    BucketSpaceDistributionConfigs ret;
    ret.space_configs.emplace(document::FixedBucketSpaces::global_space(), GlobalBucketSpaceDistributionConverter::convert_to_global(*distribution));
    ret.space_configs.emplace(document::FixedBucketSpaces::default_space(), std::move(distribution));
    return ret;
}

}
