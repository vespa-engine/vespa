// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/doc_vector_access.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/searchlib/tensor/random_level_generator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("hnsw_index_test");

using vespalib::GenerationHandler;
using vespalib::MemoryUsage;
using namespace search::tensor;

template <typename FloatType>
class MyDocVectorAccess : public DocVectorAccess {
private:
    using Vector = std::vector<FloatType>;
    using ArrayRef = vespalib::ConstArrayRef<FloatType>;
    std::vector<Vector> _vectors;

public:
    MyDocVectorAccess() : _vectors() {}
    MyDocVectorAccess& set(uint32_t docid, const Vector& vec) {
        if (docid >= _vectors.size()) {
            _vectors.resize(docid + 1);
        }
        _vectors[docid] = vec;
        return *this;
    }
    vespalib::tensor::TypedCells get_vector(uint32_t docid) const override {
        ArrayRef ref(_vectors[docid]);
        return vespalib::tensor::TypedCells(ref);
    }
};

struct LevelGenerator : public RandomLevelGenerator {
    uint32_t level;
    LevelGenerator() : level(0) {}
    uint32_t max_level() override { return level; }
};

using FloatVectors = MyDocVectorAccess<float>;
using FloatSqEuclideanDistance = SquaredEuclideanDistance<float>;
using HnswIndexUP = std::unique_ptr<HnswIndex>;

class HnswIndexTest : public ::testing::Test {
public:
    FloatVectors vectors;
    LevelGenerator* level_generator;
    GenerationHandler gen_handler;
    HnswIndexUP index;

    HnswIndexTest()
        : vectors(),
          level_generator(),
          gen_handler(),
          index()
    {
        vectors.set(1, {2, 2}).set(2, {3, 2}).set(3, {2, 3})
               .set(4, {1, 2}).set(5, {8, 3}).set(6, {7, 2})
               .set(7, {3, 5}).set(8, {0, 3}).set(9, {4, 5});
    }
    void init(bool heuristic_select_neighbors) {
        auto generator = std::make_unique<LevelGenerator>();
        level_generator = generator.get();
        index = std::make_unique<HnswIndex>(vectors, std::make_unique<FloatSqEuclideanDistance>(),
                                            std::move(generator),
                                            HnswIndex::Config(5, 2, 10, heuristic_select_neighbors));
    }
    void add_document(uint32_t docid, uint32_t max_level = 0) {
        level_generator->level = max_level;
        index->add_document(docid);
        commit();
    }
    void remove_document(uint32_t docid) {
        index->remove_document(docid);
        commit();
    }
    void commit() {
        index->transfer_hold_lists(gen_handler.getCurrentGeneration());
        gen_handler.incGeneration();
        gen_handler.updateFirstUsedGeneration();
        index->trim_hold_lists(gen_handler.getFirstUsedGeneration());
    }
    GenerationHandler::Guard take_read_guard() {
        return gen_handler.takeGuard();
    }
    MemoryUsage memory_usage() const {
        return index->memory_usage();
    }
    void expect_entry_point(uint32_t exp_docid, uint32_t exp_level) {
        EXPECT_EQ(exp_docid, index->get_entry_docid());
        EXPECT_EQ(exp_level, index->get_entry_level());
    }
    void expect_level_0(uint32_t docid, const HnswNode::LinkArray& exp_links) {
        auto node = index->get_node(docid);
        ASSERT_EQ(1, node.size());
        EXPECT_EQ(exp_links, node.level(0));
    }
    void expect_empty_level_0(uint32_t docid) {
        auto node = index->get_node(docid);
        EXPECT_TRUE(node.empty());
    }
    void expect_levels(uint32_t docid, const HnswNode::LevelArray& exp_levels) {
        auto act_node = index->get_node(docid);
        ASSERT_EQ(exp_levels.size(), act_node.size());
        EXPECT_EQ(exp_levels, act_node.levels());
    }
    void expect_top_3(uint32_t docid, std::vector<uint32_t> exp_hits) {
        uint32_t k = 3;
        auto qv = vectors.get_vector(docid);
        auto rv = index->top_k_candidates(qv, k).peek();
        std::sort(rv.begin(), rv.end(), LesserDistance());
        size_t idx = 0;
        for (const auto & hit : rv) {
            if (idx < exp_hits.size()) {
                EXPECT_EQ(hit.docid, exp_hits[idx++]);
            }
        }
        if (exp_hits.size() == k) {
            std::vector<uint32_t> expected_by_docid = exp_hits;
            std::sort(expected_by_docid.begin(), expected_by_docid.end());
            auto got_by_docid = index->find_top_k(k, qv, k);
            for (idx = 0; idx < k; ++idx) {
                EXPECT_EQ(expected_by_docid[idx], got_by_docid[idx].docid);
            }
        }
    }
};


TEST_F(HnswIndexTest, 2d_vectors_inserted_in_level_0_graph_with_simple_select_neighbors)
{
    init(false);

    add_document(1);
    expect_level_0(1, {});

    add_document(2);
    expect_level_0(1, {2});
    expect_level_0(2, {1});

    add_document(3);
    expect_level_0(1, {2, 3});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2});

    add_document(4);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2, 4});
    expect_level_0(4, {1, 3});

    add_document(5);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5});
    expect_level_0(3, {1, 2, 4, 5});
    expect_level_0(4, {1, 3});
    expect_level_0(5, {2, 3});

    add_document(6);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6});
    expect_level_0(3, {1, 2, 4, 5});
    expect_level_0(4, {1, 3});
    expect_level_0(5, {2, 3, 6});
    expect_level_0(6, {2, 5});

    add_document(7);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6, 7});
    expect_level_0(3, {1, 2, 4, 5, 7});
    expect_level_0(4, {1, 3});
    expect_level_0(5, {2, 3, 6});
    expect_level_0(6, {2, 5});
    expect_level_0(7, {2, 3});

    expect_top_3(1, {1});
    expect_top_3(2, {2, 1, 3});
    expect_top_3(3, {3});
    expect_top_3(4, {4, 1, 3});
    expect_top_3(5, {5, 6, 2});
    expect_top_3(6, {6, 5, 2});
    expect_top_3(7, {7, 3, 2});
    expect_top_3(8, {4, 3, 1});
    expect_top_3(9, {7, 3, 2});
}

TEST_F(HnswIndexTest, 2d_vectors_inserted_and_removed)
{
    init(false);

    add_document(1);
    expect_level_0(1, {});
    expect_entry_point(1, 0);

    add_document(2);
    expect_level_0(1, {2});
    expect_level_0(2, {1});
    expect_entry_point(1, 0);

    add_document(3);
    expect_level_0(1, {2, 3});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2});
    expect_entry_point(1, 0);

    remove_document(2);
    expect_level_0(1, {3});
    expect_level_0(3, {1});
    expect_entry_point(1, 0);

    remove_document(1);
    expect_level_0(3, {});
    expect_entry_point(3, 0);

    remove_document(3);
    expect_entry_point(0, -1);
}

TEST_F(HnswIndexTest, 2d_vectors_inserted_in_hierarchic_graph_with_heuristic_select_neighbors)
{
    init(true);

    add_document(1);
    expect_entry_point(1, 0);
    expect_level_0(1, {});

    add_document(2);
    expect_entry_point(1, 0);
    expect_level_0(1, {2});
    expect_level_0(2, {1});

    // Doc 3 is also added to level 1
    add_document(3, 1);
    expect_entry_point(3, 1);
    expect_level_0(1, {2, 3});
    expect_level_0(2, {1, 3});
    expect_levels(3, {{1, 2}, {}});

    // Doc 4 is closest to 1 and they are linked.
    // Doc 4 is NOT linked to 3 as the distance between 4 and 3 is greater than the distance between 3 and 1.
    // Doc 3 is therefore reachable via 1. Same argument for why doc 4 is not linked to 2.
    add_document(4);
    expect_entry_point(3, 1);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3});
    expect_levels(3, {{1, 2}, {}});
    expect_level_0(4, {1});

    // Doc 5 is closest to 2 and they are linked.
    // The other docs are reachable via 2, and no other links are created. Same argument as with doc 4 above.
    add_document(5);
    expect_entry_point(3, 1);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5});
    expect_levels(3, {{1, 2}, {}});
    expect_level_0(4, {1});
    expect_level_0(5, {2});

    // Doc 6 is closest to 5 and they are linked.
    // Doc 6 is also linked to 2 as the distance between 6 and 2 is less than the distance between 2 and 5.
    // Doc 6 is also added to level 1 and 2, and linked to doc 3 in level 1.
    add_document(6, 2);
    expect_entry_point(6, 2);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6});
    expect_levels(3, {{1, 2}, {6}});
    expect_level_0(4, {1});
    expect_level_0(5, {2, 6});
    expect_levels(6, {{2, 5}, {3}, {}});

    // Doc 7 is closest to 3 and they are linked.
    // Doc 7 is also linked to 6 as the distance between 7 and 6 is less than the distance between 6 and 3.
    // Docs 1, 2, 4 are reachable via 3.
    add_document(7);
    expect_entry_point(6, 2);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6});
    expect_levels(3, {{1, 2, 7}, {6}});
    expect_level_0(4, {1});
    expect_level_0(5, {2, 6});
    expect_levels(6, {{2, 5, 7}, {3}, {}});
    expect_level_0(7, {3, 6});
}

TEST_F(HnswIndexTest, manual_insert)
{
    init(false);

    std::vector<uint32_t> nbl;
    HnswNode empty{nbl};
    index->set_node(1, empty);
    index->set_node(2, empty);

    HnswNode three{{1,2}};
    index->set_node(3, three);
    expect_level_0(1, {3});
    expect_level_0(2, {3});
    expect_level_0(3, {1,2});

    expect_entry_point(1, 0);

    HnswNode twolevels{{{1},nbl}};
    index->set_node(4, twolevels);

    expect_entry_point(4, 1);
    expect_level_0(1, {3,4});

    HnswNode five{{{1,2}, {4}}};
    index->set_node(5, five);

    expect_levels(1, {{3,4,5}});
    expect_levels(2, {{3,5}});
    expect_levels(3, {{1,2}});
    expect_levels(4, {{1}, {5}});
    expect_levels(5, {{1,2}, {4}});
}

TEST_F(HnswIndexTest, memory_is_reclaimed_when_doing_changes_to_graph)
{
    init(true);

    add_document(1);
    add_document(3);
    auto mem_1 = memory_usage();

    add_document(2);
    expect_level_0(1, {2,3});
    expect_level_0(2, {1,3});
    expect_level_0(3, {1,2});
    auto mem_2 = memory_usage();
    // We should use more memory with larger link arrays and extra document.
    EXPECT_GT((mem_2.usedBytes() - mem_2.deadBytes()), (mem_1.usedBytes() - mem_1.deadBytes()));
    EXPECT_EQ(0, mem_2.allocatedBytesOnHold());

    remove_document(2);
    expect_level_0(1, {3});
    expect_empty_level_0(2);
    expect_level_0(3, {1});
    auto mem_3 = memory_usage();
    // We end up in the same state as before document 2 was added and effectively use the same amount of memory.
    EXPECT_EQ((mem_1.usedBytes() - mem_1.deadBytes()), (mem_3.usedBytes() - mem_3.deadBytes()));
    EXPECT_EQ(0, mem_3.allocatedBytesOnHold());
}

TEST_F(HnswIndexTest, memory_is_put_on_hold_while_read_guard_is_held)
{
    init(true);

    add_document(1);
    add_document(3);
    {
        auto guard = take_read_guard();
        add_document(2);
        auto mem = memory_usage();
        // As read guard is held memory to reclaim is put on hold
        EXPECT_GT(mem.allocatedBytesOnHold(), 0);
    }
    commit();
    auto mem = memory_usage();
    EXPECT_EQ(0, mem.allocatedBytesOnHold());
}

TEST_F(HnswIndexTest, shrink_called_simple)
{
    init(false);
    std::vector<uint32_t> nbl;
    HnswNode empty{nbl};
    index->set_node(1, empty);
    nbl.push_back(1);
    HnswNode nb1{nbl};
    index->set_node(2, nb1);
    index->set_node(3, nb1);
    index->set_node(4, nb1);
    index->set_node(5, nb1);
    expect_level_0(1, {2,3,4,5});
    index->set_node(6, nb1);
    expect_level_0(1, {2,3,4,5,6});
    expect_level_0(2, {1});
    expect_level_0(3, {1});
    expect_level_0(4, {1});
    expect_level_0(5, {1});
    expect_level_0(6, {1});
    index->set_node(7, nb1);
    expect_level_0(1, {2,3,4,6,7});
    expect_level_0(5, {});
    expect_level_0(6, {1});
    index->set_node(8, nb1);
    expect_level_0(1, {2,3,4,7,8});
    expect_level_0(6, {});
    index->set_node(9, nb1);
    expect_level_0(1, {2,3,4,7,8});
    expect_level_0(2, {1});
    expect_level_0(3, {1});
    expect_level_0(4, {1});
    expect_level_0(5, {});
    expect_level_0(6, {});
    expect_level_0(7, {1});
    expect_level_0(8, {1});
    expect_level_0(9, {});
    EXPECT_TRUE(index->check_link_symmetry());
}

TEST_F(HnswIndexTest, shrink_called_heuristic)
{
    init(true);
    std::vector<uint32_t> nbl;
    HnswNode empty{nbl};
    index->set_node(1, empty);
    nbl.push_back(1);
    HnswNode nb1{nbl};
    index->set_node(2, nb1);
    index->set_node(3, nb1);
    index->set_node(4, nb1);
    index->set_node(5, nb1);
    expect_level_0(1, {2,3,4,5});
    index->set_node(6, nb1);
    expect_level_0(1, {2,3,4,5,6});
    expect_level_0(2, {1});
    expect_level_0(3, {1});
    expect_level_0(4, {1});
    expect_level_0(5, {1});
    expect_level_0(6, {1});
    index->set_node(7, nb1);
    expect_level_0(1, {2,3,4});
    expect_level_0(2, {1});
    expect_level_0(3, {1});
    expect_level_0(4, {1});
    expect_level_0(5, {});
    expect_level_0(6, {});
    expect_level_0(7, {});
    index->set_node(8, nb1);
    index->set_node(9, nb1);
    expect_level_0(1, {2,3,4,8,9});
    EXPECT_TRUE(index->check_link_symmetry());
}

GTEST_MAIN_RUN_ALL_TESTS()

