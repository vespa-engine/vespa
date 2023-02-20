// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/hnsw_nodeid_mapping.h>
#include <vespa/searchlib/tensor/hnsw_node.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::tensor;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;

class HnswNodeidMappingTest : public ::testing::Test {
public:
    using NodeidVector = std::vector<uint32_t>;
    using NodeidVectorVector = std::vector<NodeidVector>;
    HnswNodeidMapping mapping;

    HnswNodeidMappingTest()
        : mapping()
    {
        mapping.assign_generation(10);
    }
    void expect_allocate_get(const NodeidVector& exp_ids, uint32_t docid) {
        auto ids = mapping.allocate_ids(docid, exp_ids.size());
        EXPECT_EQ(exp_ids, NodeidVector(ids.begin(), ids.end()));
        expect_get(exp_ids, docid);
    }

    void expect_get(const NodeidVector& exp_ids, uint32_t docid) {
        auto ids = mapping.get_ids(docid);
        EXPECT_EQ(exp_ids, NodeidVector(ids.begin(), ids.end()));
    }
    NodeidVector get_id_vector(uint32_t docid) {
        auto ids = mapping.get_ids(docid);
        return { ids.begin(), ids.end() };
    }
    NodeidVectorVector get_id_vectors(uint32_t docid_limit) {
        NodeidVectorVector id_vectors;
        id_vectors.reserve(docid_limit);
        for (uint32_t docid = 0; docid < docid_limit; ++docid) {
            id_vectors.push_back(get_id_vector(docid));
        }
        return id_vectors;
    }
    void expect_id_vectors(const NodeidVectorVector& exp) {
        for (uint32_t docid = 0; docid < exp.size(); ++docid) {
            EXPECT_EQ(exp[docid], get_id_vector(docid));
        }
    }
    void drop_held_memory() {
        mapping.assign_generation(1);
        mapping.reclaim_memory(2);
    }
};


TEST_F(HnswNodeidMappingTest, allocate_and_get_nodeids)
{
    expect_allocate_get({}, 1);
    expect_allocate_get({1}, 30);
    expect_allocate_get({2, 3, 4}, 40);
    expect_allocate_get({5, 6}, 50);
    // Note that docid=2 has implicit no nodeids:
    expect_get({}, 2);
}

TEST_F(HnswNodeidMappingTest, free_ids_clears_docid_entry_so_it_can_be_reused)
{
    expect_allocate_get({1, 2, 3}, 1);
    mapping.free_ids(1);
    expect_get({}, 1);

    expect_allocate_get({4, 5}, 1);
    mapping.free_ids(1);
    expect_get({}, 1);
}

TEST_F(HnswNodeidMappingTest, free_ids_puts_nodeids_on_hold_list_and_then_free_list_for_reuse)
{
    expect_allocate_get({1, 2, 3}, 1);
    expect_allocate_get({4, 5, 6}, 2);

    mapping.free_ids(1); // {1, 2, 3} are inserted into hold list
    mapping.assign_generation(11);

    expect_allocate_get({7, 8}, 3); // Free list is NOT used
    mapping.reclaim_memory(12); // {1, 2, 3} are moved to free list
    expect_allocate_get({3, 2}, 4); // Free list is used

    mapping.free_ids(2); // {4, 5, 6} are inserted into hold list
    mapping.assign_generation(12);
    mapping.free_ids(3); // {7, 8} are inserted into hold list
    mapping.assign_generation(13);

    mapping.reclaim_memory(13); // {4, 5, 6} are moved to free list
    expect_allocate_get({6, 5}, 5); // Free list is used
    expect_allocate_get({4, 1, 9}, 6); // Free list is first used, then new nodeid is allocated

    mapping.reclaim_memory(14); // {7, 8} are moved to free list
    expect_allocate_get({8, 7, 10}, 7); // Free list is first used, then new nodeid is allocated
}

TEST_F(HnswNodeidMappingTest, on_load_populates_mapping)
{
    std::vector<HnswNode> nodes(10);
    nodes[1].levels_ref().store_relaxed(EntryRef(1));
    nodes[1].store_docid(7);
    nodes[1].store_subspace(0);
    nodes[2].levels_ref().store_relaxed(EntryRef(2));
    nodes[2].store_docid(4);
    nodes[2].store_subspace(0);
    nodes[7].levels_ref().store_relaxed(EntryRef(3));
    nodes[7].store_docid(4);
    nodes[7].store_subspace(1);
    mapping.on_load(vespalib::ConstArrayRef(nodes.data(), 9));
    expect_get({1}, 7);
    expect_get({2, 7}, 4);
    // Drain free list when allocating nodeids.
    expect_allocate_get({3, 4, 5, 6, 8, 9, 10}, 1);
}

TEST_F(HnswNodeidMappingTest, memory_usage_increases_when_allocating_nodeids)
{
    expect_allocate_get({1, 2}, 1);
    auto a = mapping.memory_usage();
    EXPECT_GT(a.allocatedBytes(), 0);
    EXPECT_GT(a.usedBytes(), 0);
    EXPECT_GE(a.allocatedBytes(), a.usedBytes());

    expect_allocate_get({3, 4}, 2);
    auto b = mapping.memory_usage();
    EXPECT_GT(b.usedBytes(), a.usedBytes());
}

TEST_F(HnswNodeidMappingTest, compaction_works)
{
    const uint32_t docid_limit = 20000;
    const uint32_t min_multinode_docid = 4;
    for (uint32_t docid = 1; docid < docid_limit; ++docid) {
        mapping.allocate_ids(docid, 1);
    }
    CompactionStrategy compaction_strategy;
    (void) mapping.update_stat(compaction_strategy);
    EXPECT_FALSE(mapping.consider_compact());
    for (uint32_t docid = min_multinode_docid; docid < docid_limit; ++docid) {
        mapping.free_ids(docid);
        drop_held_memory();
        mapping.allocate_ids(docid, 2);
    }
    auto id_vectors = get_id_vectors(docid_limit);
    auto mem_before = mapping.update_stat(compaction_strategy);
    EXPECT_EQ(0, mem_before.allocatedBytesOnHold());
    EXPECT_LT(0, mem_before.usedBytes());
    EXPECT_TRUE(mapping.consider_compact());
    mapping.compact_worst(compaction_strategy);
    EXPECT_FALSE(mapping.consider_compact());
    auto mem_after = mapping.update_stat(compaction_strategy);
    drop_held_memory();
    auto mem_after_drop = mapping.update_stat(compaction_strategy);
    EXPECT_LT(0, mem_after.allocatedBytesOnHold());
    EXPECT_LT(mem_before.usedBytes(), mem_after.usedBytes());
    EXPECT_GT(mem_before.deadBytes(), mem_after.deadBytes());
    EXPECT_EQ(0, mem_after_drop.allocatedBytesOnHold());
    EXPECT_GT(mem_before.usedBytes(), mem_after_drop.usedBytes());
    expect_id_vectors(id_vectors);
}

GTEST_MAIN_RUN_ALL_TESTS()

