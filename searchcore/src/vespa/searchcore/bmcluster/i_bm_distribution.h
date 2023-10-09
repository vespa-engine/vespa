// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace document { class Bucket; }

namespace vespa::config::content::internal {
    class InternalStorDistributionType;
}

namespace storage::lib { class ClusterStateBundle; }

namespace search::bmcluster {

/*
 * Interface class for describing cluster toplogy and how messages are
 * routed from feeders into the cluster.
 */
class IBmDistribution {
public:
    using DistributionConfigBuilder = vespa::config::content::internal::InternalStorDistributionType;
    using DistributionConfig = const DistributionConfigBuilder;

    virtual ~IBmDistribution() = default;
    virtual uint32_t get_num_nodes() const = 0;
    virtual uint32_t get_service_layer_node_idx(const document::Bucket & bucket) const = 0;
    virtual uint32_t get_distributor_node_idx(const document::Bucket & bucket) const = 0;
    virtual DistributionConfig get_distribution_config() const = 0;
    virtual storage::lib::ClusterStateBundle get_cluster_state_bundle() const = 0;
};

};
