// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/filestorage/has_mask_remapper.h>
#include <gtest/gtest.h>

namespace storage {

using NodeList = std::vector<api::MergeBucketCommand::Node>;

const NodeList merge_operation_nodes{{0, true}, {1, true}, {2, false}, {3, false}, {4, false}};

TEST(HasMaskRemapperTest, test_remap_none)
{
    HasMaskRemapper remap_mask(merge_operation_nodes, merge_operation_nodes);
    for (uint32_t i = 0; i < (1u << merge_operation_nodes.size()); ++i) {
        EXPECT_EQ(i, remap_mask(i));
    }
    EXPECT_EQ(31u, remap_mask(255u));
}

TEST(HasMaskRemapperTest, test_remap_subset)
{
    NodeList reply_nodes{{0, true}, {1, true}, {3, false}};
    HasMaskRemapper remap_mask(merge_operation_nodes, reply_nodes);
    std::vector<uint16_t> remapped;
    for (uint32_t i = 0; i < (1u << reply_nodes.size()); ++i) {
        remapped.push_back(remap_mask(i));
    }
    EXPECT_EQ((std::vector<uint16_t>{0u, 1u, 2u, 3u, 8u, 9u, 10u, 11u}), remapped);
}

TEST(HasMaskRemapperTest, test_remap_swapped_subset)
{
    NodeList reply_nodes{{1, true}, {0, true}};
    HasMaskRemapper remap_mask(merge_operation_nodes, reply_nodes);
    std::vector<uint16_t> remapped;
    for (uint32_t i = 0; i < (1u << reply_nodes.size()); ++i) {
        remapped.push_back(remap_mask(i));
    }
    EXPECT_EQ((std::vector<uint16_t>{0u, 2u, 1u, 3u}), remapped);
}

TEST(HasMaskRemapperTest, test_keep_unremapped_bits)
{
    NodeList reply_nodes{{0, true}, {1, true}, {3, false}};
    HasMaskRemapper remap_mask(merge_operation_nodes, reply_nodes);
    EXPECT_EQ(20u, remap_mask(0u, (1u << 5) - 1));
    EXPECT_EQ(11u, remap_mask((1u << 3) - 1, 0u));
    EXPECT_EQ(31u, remap_mask((1u << 3) - 1, (1u << 5) - 1));
    EXPECT_EQ(24u, remap_mask(4u, 16u));
    HasMaskRemapper same_nodes_remap_mask(merge_operation_nodes, merge_operation_nodes);
    EXPECT_EQ(31u, same_nodes_remap_mask(255u, 0u));
    EXPECT_EQ(224u, same_nodes_remap_mask(0u, 255u));
    EXPECT_EQ(255u, same_nodes_remap_mask(255u, 255u));
}

}
