// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-stor-distribution.h>
#include <memory>

namespace storage::lib { class Distribution; }
namespace storage {

struct GlobalBucketSpaceDistributionConverter {
    using DistributionConfig = vespa::config::content::StorDistributionConfig;
    // TODO remove legacy_mode flags on Vespa 8 - this is a workaround for https://github.com/vespa-engine/vespa/issues/8475
    static std::shared_ptr<DistributionConfig> convert_to_global(const DistributionConfig&, bool legacy_mode = false);
    static std::shared_ptr<lib::Distribution>  convert_to_global(const lib::Distribution&, bool legacy_mode = false);

    // Helper functions which may be of use outside this class
    static std::unique_ptr<DistributionConfig> string_to_config(const vespalib::string&);
    static vespalib::string config_to_string(const DistributionConfig&);
};

}
