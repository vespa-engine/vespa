// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "node_supported_features_repo.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace storage::distributor {

NodeSupportedFeaturesRepo::NodeSupportedFeaturesRepo() = default;

NodeSupportedFeaturesRepo::NodeSupportedFeaturesRepo(
        vespalib::hash_map<uint16_t, NodeSupportedFeatures> features,
        PrivateCtorTag) noexcept
    : _node_features(std::move(features))
{}

NodeSupportedFeaturesRepo::~NodeSupportedFeaturesRepo() = default;

const NodeSupportedFeatures&
NodeSupportedFeaturesRepo::node_supported_features(uint16_t node_idx) const noexcept
{
    static const NodeSupportedFeatures default_features;
    const auto iter = _node_features.find(node_idx);
    return (iter != _node_features.end() ? iter->second : default_features);
}

std::shared_ptr<const NodeSupportedFeaturesRepo>
NodeSupportedFeaturesRepo::make_union_of(const vespalib::hash_map<uint16_t, NodeSupportedFeatures>& node_features) const
{
    auto new_features = _node_features; // Must be by copy.
    // We always let the _new_ features update any existing mapping.
    for (const auto& nf : node_features) {
        new_features[nf.first] = nf.second;
    }
    return std::make_shared<NodeSupportedFeaturesRepo>(std::move(new_features), PrivateCtorTag{});
}

}
