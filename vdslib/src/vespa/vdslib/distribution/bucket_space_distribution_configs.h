// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "distribution.h"
#include <vespa/document/bucket/bucketspace.h>
#include <map>
#include <memory>

namespace storage::lib {

/**
 * Represents a complete mapping of all known bucket spaces to their appropriate,
 * (possibly derived) distribution config.
 */
struct BucketSpaceDistributionConfigs {
    // TODO hash_map
    std::map<document::BucketSpace, std::shared_ptr<const Distribution>> space_configs;

    [[nodiscard]] std::shared_ptr<const Distribution> get_or_nullptr(document::BucketSpace space) const noexcept {
        auto iter = space_configs.find(space);
        return (iter != space_configs.end()) ? iter->second : std::shared_ptr<const Distribution>();
    }

    static BucketSpaceDistributionConfigs from_default_distribution(std::shared_ptr<const Distribution>);
};

}
