// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucket_space_distribution_configs.h"
#include "distribution.h"
#include <iosfwd>

namespace storage::lib {

class DistributionConfigBundle {
    std::unique_ptr<const Distribution::DistributionConfig> _config;
    std::shared_ptr<const Distribution>                     _default_distribution;
    BucketSpaceDistributionConfigs                          _bucket_space_distributions;
    uint16_t                                                _total_node_count;
    uint16_t                                                _total_leaf_group_count;
public:
    // TODO shared_ptr?
    explicit DistributionConfigBundle(std::unique_ptr<const Distribution::DistributionConfig> config);
    explicit DistributionConfigBundle(Distribution::ConfigWrapper config);
    ~DistributionConfigBundle();

    // TODO config_sp?
    [[nodiscard]] const Distribution::DistributionConfig& config() const noexcept { return *_config; }

    [[nodiscard]] const Distribution& default_distribution() const noexcept { return *_default_distribution; }
    [[nodiscard]] const std::shared_ptr<const Distribution>& default_distribution_sp() const noexcept {
        return _default_distribution;
    }
    [[nodiscard]] std::shared_ptr<const Distribution> bucket_space_distribution_or_nullptr(document::BucketSpace space) const noexcept {
        return _bucket_space_distributions.get_or_nullptr(space);
    }

    [[nodiscard]] uint16_t total_node_count() const noexcept { return _total_node_count; }
    [[nodiscard]] uint16_t total_leaf_group_count() const noexcept { return _total_leaf_group_count; }
    // Note: these apply to the default space only
    [[nodiscard]] uint16_t redundancy() const noexcept { return _default_distribution->getRedundancy(); }
    [[nodiscard]] uint16_t searchable_copies() const noexcept { return _default_distribution->getReadyCopies(); }

    bool operator==(const DistributionConfigBundle& rhs) const noexcept;
    bool operator!=(const DistributionConfigBundle& rhs) const noexcept {
        return !(*this == rhs);
    }

    [[nodiscard]] static std::shared_ptr<DistributionConfigBundle> of(Distribution::ConfigWrapper cfg);
    [[nodiscard]] static std::shared_ptr<DistributionConfigBundle> of(std::unique_ptr<const Distribution::DistributionConfig> cfg);
};

}
