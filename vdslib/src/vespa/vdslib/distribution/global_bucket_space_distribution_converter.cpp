// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "global_bucket_space_distribution_converter.h"
#include <vespa/config-stor-distribution.h>
#include <vespa/config/print/asciiconfigwriter.h>
#include <vespa/config/print/asciiconfigreader.hpp>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage::lib {

using DistributionConfig = vespa::config::content::StorDistributionConfig;

std::shared_ptr<const lib::Distribution>
GlobalBucketSpaceDistributionConverter::convert_to_global(const lib::Distribution& distr) {
    // TODO just simplify to a to_global() member function instead?
    return std::make_shared<const lib::Distribution>(distr, true);
}

std::unique_ptr<DistributionConfig>
GlobalBucketSpaceDistributionConverter::string_to_config(const std::string& cfg) {
    vespalib::asciistream iss(cfg);
    config::AsciiConfigReader<vespa::config::content::StorDistributionConfig> reader(iss);
    return reader.read();
}

}
