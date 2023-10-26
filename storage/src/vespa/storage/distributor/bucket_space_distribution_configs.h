// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <map>
#include <memory>

namespace storage::lib { class Distribution; }

namespace storage::distributor {

/**
 * Represents a complete mapping of all known bucket spaces to their appropriate,
 * (possibly derived) distribution config.
 */
struct BucketSpaceDistributionConfigs {
    std::map<document::BucketSpace, std::shared_ptr<const lib::Distribution>> space_configs;

    std::shared_ptr<const lib::Distribution> get_or_nullptr(document::BucketSpace space) const noexcept {
        auto iter = space_configs.find(space);
        return (iter != space_configs.end()) ? iter->second : std::shared_ptr<const lib::Distribution>();
    }

    static BucketSpaceDistributionConfigs from_default_distribution(std::shared_ptr<const lib::Distribution>);
};

}
