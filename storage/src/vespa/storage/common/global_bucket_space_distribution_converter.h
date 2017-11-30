// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/config-stor-distribution.h>
#include <memory>

namespace storage {

struct GlobalBucketSpaceDistributionConverter {
    using DistributionConfig = vespa::config::content::StorDistributionConfig;
    static std::shared_ptr<DistributionConfig> convert_to_global(const DistributionConfig&);
};

}