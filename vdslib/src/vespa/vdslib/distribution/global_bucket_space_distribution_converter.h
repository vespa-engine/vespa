// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distribution.h"
#include <memory>

namespace storage::lib {

struct GlobalBucketSpaceDistributionConverter {
    using DistributionConfig = Distribution::DistributionConfig;

    static std::shared_ptr<DistributionConfig> convert_to_global(const DistributionConfig&);
    static std::shared_ptr<lib::Distribution>  convert_to_global(const lib::Distribution&);

    // Helper functions which may be of use outside this class
    static std::unique_ptr<DistributionConfig> string_to_config(const vespalib::string&);
    static vespalib::string config_to_string(const DistributionConfig&);
};

}
