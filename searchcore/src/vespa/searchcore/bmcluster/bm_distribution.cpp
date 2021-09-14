// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_distribution.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

using storage::lib::ClusterStateBundle;

namespace search::bmcluster {

using DistributionConfigBuilder = BmDistribution::DistributionConfigBuilder;

namespace {

BmDistribution::DistributionConfig
make_distribution_config(uint32_t num_nodes)
{
    DistributionConfigBuilder dc;
    {
        DistributionConfigBuilder::Group group;
        {
            for (uint32_t i = 0; i < num_nodes; ++i) {
                DistributionConfigBuilder::Group::Nodes node;
                node.index = i;
                group.nodes.push_back(std::move(node));
            }
        }
        group.index = "invalid";
        group.name = "invalid";
        group.capacity = 1.0;
        group.partitions = "";
        dc.group.push_back(std::move(group));
    }
    dc.redundancy = 1;
    dc.readyCopies = 1;
    return dc;
}

ClusterStateBundle
make_cluster_state_bundle(uint32_t num_nodes)
{
    vespalib::asciistream s;
    s << "version:2 distributor:" << num_nodes << " storage:" << num_nodes;
    storage::lib::ClusterStateBundle bundle(storage::lib::ClusterState(s.str()));
    return bundle;
}

}

BmDistribution::BmDistribution(uint32_t num_nodes)
    : _num_nodes(num_nodes),
      _distribution_config(make_distribution_config(num_nodes)),
      _distribution(_distribution_config),
      _cluster_state_bundle(make_cluster_state_bundle(num_nodes))
{
}

BmDistribution::~BmDistribution()
{
}

uint32_t
BmDistribution::get_num_nodes() const
{
    return _num_nodes;

}

uint32_t
BmDistribution::get_service_layer_node_idx(const document::Bucket& bucket) const
{
    auto cluster_state = _cluster_state_bundle.getDerivedClusterState(bucket.getBucketSpace());
    auto nodes = _distribution.getIdealStorageNodes(*cluster_state, bucket.getBucketId());
    assert(!nodes.empty());
    return nodes[0];
}

uint32_t
BmDistribution::get_distributor_node_idx(const document::Bucket& bucket) const
{
    auto cluster_state = _cluster_state_bundle.getDerivedClusterState(bucket.getBucketSpace());
    return _distribution.getIdealDistributorNode(*cluster_state, bucket.getBucketId());
}

BmDistribution::DistributionConfig
BmDistribution::get_distribution_config() const
{
    return _distribution_config;
}

ClusterStateBundle
BmDistribution::get_cluster_state_bundle() const
{
    return _cluster_state_bundle;
}

};
