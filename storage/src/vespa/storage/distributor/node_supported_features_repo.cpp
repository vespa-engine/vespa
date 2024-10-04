// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "node_supported_features_repo.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <algorithm>
#include <numeric>

namespace storage::distributor {

namespace {

NodeSupportedFeatures feature_intersection(const vespalib::hash_map<uint16_t, NodeSupportedFeatures>& features) {
    // TODO replace with C++23 std::ranges::fold_left_first() once supported
    if (features.empty()) {
        return {};
    }
    auto iter = features.begin();
    auto first = iter->second;
    ++iter;
    return std::accumulate(iter, features.end(), first, [](const auto& accu, const auto& v) {
        return accu.intersection_of(v.second);
    });
}

}

NodeSupportedFeaturesRepo::NodeSupportedFeaturesRepo() = default;

NodeSupportedFeaturesRepo::NodeSupportedFeaturesRepo(
        vespalib::hash_map<uint16_t, NodeSupportedFeatures> features,
        PrivateCtorTag) noexcept
    : _node_features(std::move(features)),
      _supported_by_all_nodes(feature_intersection(_node_features))
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
