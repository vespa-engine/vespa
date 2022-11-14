// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/hnsw_nodeid_mapping.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::tensor;

class HnswNodeidMappingTest : public ::testing::Test {
public:
    using NodeidVector = std::vector<uint32_t>;
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

GTEST_MAIN_RUN_ALL_TESTS()

