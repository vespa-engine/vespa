// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

void
add_nodes_to_group(DistributionConfigBuilder::Group &group, uint32_t first_node_idx, uint32_t nodes_per_group)
{
    for (uint32_t i = 0; i < nodes_per_group; ++i) {
        DistributionConfigBuilder::Group::Nodes node;
        node.index = first_node_idx + i;
        group.nodes.push_back(std::move(node));
    }
}

BmDistribution::DistributionConfig
make_distribution_config(uint32_t nodes_per_group, uint32_t groups, uint32_t redundancy)
{
    DistributionConfigBuilder dc;
    {
        {
            DistributionConfigBuilder::Group group;
            group.index = "invalid";
            group.name = "invalid";
            group.capacity = 1.0;
            if (groups == 0u) {
                add_nodes_to_group(group, 0, nodes_per_group);
                group.partitions = "";
                dc.redundancy = redundancy;
                dc.readyCopies = redundancy;
            } else {
                vespalib::asciistream partitions;
                for (uint32_t group_idx = 0; group_idx < groups; ++group_idx) {
                    if (group_idx + 1< groups) {
                        partitions << redundancy << '|';
                    } else {
                        partitions << '*';
                    }
                }
                group.partitions = partitions.str();
                dc.redundancy = redundancy * groups;
                dc.readyCopies = redundancy * groups;
            }
            dc.group.push_back(std::move(group));
        }
        uint32_t node_idx = 0;
        for (uint32_t group_idx = 0; group_idx < groups; ++group_idx) {
            DistributionConfigBuilder::Group group;
            group.index = std::to_string(group_idx);
            group.name = "group_" + group.index;
            group.capacity = 1.0;
            group.partitions = "";
            add_nodes_to_group(group, node_idx, nodes_per_group);
            node_idx += nodes_per_group;
            dc.group.push_back(std::move(group));
        }
    }
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

BmDistribution::BmDistribution(uint32_t groups, uint32_t nodes_per_group, uint32_t redundancy)
    : _num_nodes(std::max(1u, groups) * nodes_per_group),
      _distribution_config(make_distribution_config(nodes_per_group, groups, redundancy)),
      _distribution(_distribution_config),
      _pending_cluster_state(make_cluster_state(_num_nodes)),
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
