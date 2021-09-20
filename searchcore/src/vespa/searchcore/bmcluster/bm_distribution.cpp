// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_distribution.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

using storage::lib::ClusterState;
using storage::lib::ClusterStateBundle;
using storage::lib::Node;
using storage::lib::NodeState;
using storage::lib::NodeType;
using storage::lib::State;

namespace search::bmcluster {

using DistributionConfigBuilder = BmDistribution::DistributionConfigBuilder;

namespace {

BmDistribution::DistributionConfig
make_distribution_config(uint32_t num_nodes, uint32_t redundancy)
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
    dc.redundancy = redundancy;
    dc.readyCopies = redundancy;
    return dc;
}

ClusterState
make_cluster_state(uint32_t num_nodes)
{
    vespalib::asciistream s;
    s << "version:2 distributor:" << num_nodes << " storage:" << num_nodes;
    return storage::lib::ClusterState(s.str());
}

}

BmDistribution::BmDistribution(uint32_t num_nodes, uint32_t redundancy)
    : _num_nodes(num_nodes),
      _distribution_config(make_distribution_config(num_nodes, redundancy)),
      _distribution(_distribution_config),
      _pending_cluster_state(make_cluster_state(num_nodes)),
      _cluster_state_bundle(_pending_cluster_state),
      _has_pending_cluster_state(false)
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

void
BmDistribution::set_node_state(uint32_t node_idx, bool distributor, const State& state)
{
    const NodeType& node_type = distributor ? NodeType::DISTRIBUTOR : NodeType::STORAGE;
    Node node(node_type, node_idx);
    NodeState node_state(node_type, state);
    _pending_cluster_state.setNodeState(node, node_state);
    if (!_has_pending_cluster_state) {
        _pending_cluster_state.setVersion(_pending_cluster_state.getVersion() + 1);
        _has_pending_cluster_state = true;
    }
}

void
BmDistribution::set_node_state(uint32_t node_idx, const State& state)
{
    set_node_state(node_idx, false, state);
    set_node_state(node_idx, true, state);
}

void
BmDistribution::commit_cluster_state_change()
{
    if (_has_pending_cluster_state) {
        _cluster_state_bundle = ClusterStateBundle(_pending_cluster_state);
        _has_pending_cluster_state = false;
    }
}

};
