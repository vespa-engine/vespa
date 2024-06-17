// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distribution_config_bundle.h"
#include <vespa/config/print/asciiconfigreader.hpp>
#include <vespa/config-stor-distribution.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <sstream>

namespace storage::lib {

namespace {

void count_nodes_and_leaf_groups(const Group& g, uint16_t& node_count_inout, uint16_t& leaf_group_count_inout) {
    if (g.isLeafGroup()) {
        leaf_group_count_inout++;
        node_count_inout += g.getNodes().size();
    } else {
        for (const auto& sub_g : g.getSubGroups()) {
            count_nodes_and_leaf_groups(*sub_g.second, node_count_inout, leaf_group_count_inout);
        }
    }
}

std::unique_ptr<Distribution::DistributionConfig> config_from_existing_distribution(const Distribution& distr) {
    vespalib::asciistream is(distr.serialized());
    config::AsciiConfigReader<vespa::config::content::StorDistributionConfig> reader(is);
    return reader.read();
}

}

// TODO de-dupe ctors
DistributionConfigBundle::DistributionConfigBundle(std::shared_ptr<const Distribution> distr)
    : _config(config_from_existing_distribution(*distr)),
      _default_distribution(std::move(distr)),
      _bucket_space_distributions(BucketSpaceDistributionConfigs::from_default_distribution(_default_distribution)),
      _total_node_count(0),
      _total_leaf_group_count(0)
{
    count_nodes_and_leaf_groups(_default_distribution->getNodeGraph(), _total_node_count, _total_leaf_group_count);
}

DistributionConfigBundle::DistributionConfigBundle(Distribution::ConfigWrapper config)
    : DistributionConfigBundle(config.steal())
{
}

DistributionConfigBundle::DistributionConfigBundle(std::unique_ptr<const Distribution::DistributionConfig> config)
    : _config(std::move(config)),
      _default_distribution(std::make_shared<Distribution>(*_config)),
      _bucket_space_distributions(BucketSpaceDistributionConfigs::from_default_distribution(_default_distribution)),
      _total_node_count(0),
      _total_leaf_group_count(0)
{
    count_nodes_and_leaf_groups(_default_distribution->getNodeGraph(), _total_node_count, _total_leaf_group_count);
}

DistributionConfigBundle::~DistributionConfigBundle() = default;

bool DistributionConfigBundle::operator==(const DistributionConfigBundle& rhs) const noexcept {
    // Distribution caches the raw string config format internally.
    // Equality is checked using this cheap representation.
    return (*_default_distribution == *rhs._default_distribution);
}

std::shared_ptr<DistributionConfigBundle>
DistributionConfigBundle::of(std::shared_ptr<const Distribution> distr) {
    return std::make_shared<DistributionConfigBundle>(std::move(distr));
}

std::shared_ptr<DistributionConfigBundle>
DistributionConfigBundle::of(Distribution::ConfigWrapper cfg) {
    return std::make_shared<DistributionConfigBundle>(std::move(cfg));
}

std::shared_ptr<DistributionConfigBundle>
DistributionConfigBundle::of(std::unique_ptr<const Distribution::DistributionConfig> cfg) {
    return std::make_shared<DistributionConfigBundle>(std::move(cfg));
}

}
