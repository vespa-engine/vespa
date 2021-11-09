// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/node_supported_features_repo.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage::distributor {

struct NodeSupportedFeaturesRepoTest : Test {
    using FeatureMap = vespalib::hash_map<uint16_t, NodeSupportedFeatures>;
    NodeSupportedFeaturesRepo _repo;

    static NodeSupportedFeatures set_features() noexcept {
        NodeSupportedFeatures f;
        f.unordered_merge_chaining = true;
        return f;
    }

    static NodeSupportedFeatures unset_features() noexcept {
        return {};
    }
};

TEST_F(NodeSupportedFeaturesRepoTest, feature_set_is_empty_by_default) {
    EXPECT_EQ(_repo.node_supported_features(0), unset_features());
    EXPECT_EQ(_repo.node_supported_features(12345), unset_features());
}

TEST_F(NodeSupportedFeaturesRepoTest, make_union_of_can_add_new_feature_mapping) {
    FeatureMap fm;
    fm[1] = set_features();
    fm[60] = set_features();
    auto new_repo = _repo.make_union_of(fm);
    EXPECT_EQ(new_repo->node_supported_features(0), unset_features());
    EXPECT_EQ(new_repo->node_supported_features(1), set_features());
    EXPECT_EQ(new_repo->node_supported_features(60), set_features());
}

TEST_F(NodeSupportedFeaturesRepoTest, make_union_of_updates_existing_feature_mappings) {
    FeatureMap fm;
    fm[1] = set_features();
    fm[60] = set_features();
    auto new_repo = _repo.make_union_of(fm);
    fm[1] = unset_features();
    new_repo = new_repo->make_union_of(fm);
    EXPECT_EQ(new_repo->node_supported_features(1), unset_features());
    EXPECT_EQ(new_repo->node_supported_features(60), set_features());
}

}
