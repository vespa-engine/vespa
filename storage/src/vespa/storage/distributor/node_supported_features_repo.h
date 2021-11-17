// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "node_supported_features.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <memory>

namespace storage::distributor {

/**
 * Repo of known mappings from node distribution key to feature set supported by
 * the content node with the given distribution key.
 *
 * Entirely immutable; copy-on-write via make_union_of().
 */
class NodeSupportedFeaturesRepo {
    const vespalib::hash_map<uint16_t, NodeSupportedFeatures> _node_features;
    struct PrivateCtorTag {};
public:
    NodeSupportedFeaturesRepo();

    NodeSupportedFeaturesRepo(vespalib::hash_map<uint16_t, NodeSupportedFeatures> features, PrivateCtorTag) noexcept;
    ~NodeSupportedFeaturesRepo();

    // Returns supported node features for node with distribution key node_idx, or a default feature set
    // with all features unset if node has no known mapping.
    [[nodiscard]] const NodeSupportedFeatures& node_supported_features(uint16_t node_idx) const noexcept;

    // Returns a new repo instance containing the union key->features set of self and node_features.
    // If there is a duplicate mapping between the two, the features in node_features take precedence
    // and will be stored in the new repo.
    [[nodiscard]] std::shared_ptr<const NodeSupportedFeaturesRepo>
    make_union_of(const vespalib::hash_map<uint16_t, NodeSupportedFeatures>& node_features) const;
};

}
