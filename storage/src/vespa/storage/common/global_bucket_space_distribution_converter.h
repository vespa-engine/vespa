// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/config-stor-distribution.h>
#include <memory>

namespace storage {

struct GlobalBucketSpaceDistributionConverter {
    using DistributionConfig = vespa::config::content::StorDistributionConfig;
    static std::shared_ptr<DistributionConfig> convert_to_global(const DistributionConfig&);
    static std::shared_ptr<lib::Distribution>  convert_to_global(const lib::Distribution&);

    // Helper functions which may be of use outside this class
    static std::unique_ptr<DistributionConfig> string_to_config(const vespalib::string&);
    static vespalib::string config_to_string(const DistributionConfig&);
};

}
