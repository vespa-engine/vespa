// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/doc_vector_access.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/searchlib/tensor/random_level_generator.h>
#include <vespa/searchlib/tensor/inv_log_level_generator.h>
#include <vespa/searchlib/tensor/subspace_type.h>
#include <vespa/searchlib/tensor/vector_bundle.h>
#include <vespa/searchlib/queryeval/global_filter.h>
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("hnsw_index_test");

using vespalib::GenerationHandler;
using vespalib::MemoryUsage;
using namespace search::tensor;
using namespace vespalib::slime;
using vespalib::Slime;
using search::BitVector;
using vespalib::eval::get_cell_type;
using vespalib::eval::ValueType;
using vespalib::datastore::CompactionSpec;
using vespalib::datastore::CompactionStrategy;
using search::queryeval::GlobalFilter;

template <typename FloatType>
class MyDocVectorAccess : public DocVectorAccess {
private:
    using Vector = std::vector<FloatType>;
    using ArrayRef = vespalib::ConstArrayRef<FloatType>;
    std::vector<Vector> _vectors;
    SubspaceType        _subspace_type;

public:
    MyDocVectorAccess()
        : _vectors(),
          _subspace_type(ValueType::make_type(get_cell_type<FloatType>(), {{"dims", 2}}))
    {
    }
    MyDocVectorAccess& set(uint32_t docid, const Vector& vec) {
        if (docid >= _vectors.size()) {
            _vectors.resize(docid + 1);
        }
        _vectors[docid] = vec;
        return *this;
    }
    vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const override {
        (void) subspace;
        ArrayRef ref(_vectors[docid]);
        return vespalib::eval::TypedCells(ref);
    }
    VectorBundle get_vectors(uint32_t docid) const override {
        ArrayRef ref(_vectors[docid]);
        assert(_subspace_type.size() == ref.size());
        return VectorBundle(ref.data(), 1, _subspace_type);
    }

    void clear() { _vectors.clear(); }
};

struct LevelGenerator : public RandomLevelGenerator {
    uint32_t level;
    LevelGenerator() : level(0) {}
    uint32_t max_level() override { return level; }
};

using FloatVectors = MyDocVectorAccess<float>;
using HnswIndexUP = std::unique_ptr<HnswIndex>;

class HnswIndexTest : public ::testing::Test {
public:
    FloatVectors vectors;
    std::shared_ptr<GlobalFilter> global_filter;
    LevelGenerator* level_generator;
    GenerationHandler gen_handler;
    HnswIndexUP index;

    HnswIndexTest()
        : vectors(),
          global_filter(GlobalFilter::create()),
          level_generator(),
          gen_handler(),
          index()
    {
        vectors.set(1, {2, 2}).set(2, {3, 2}).set(3, {2, 3})
               .set(4, {1, 2}).set(5, {8, 3}).set(6, {7, 2})
               .set(7, {3, 5}).set(8, {0, 3}).set(9, {4, 5});
    }

    ~HnswIndexTest() {}

    void init(bool heuristic_select_neighbors) {
        auto generator = std::make_unique<LevelGenerator>();
        level_generator = generator.get();
        index = std::make_unique<HnswIndex>(vectors, std::make_unique<SquaredEuclideanDistance>(vespalib::eval::CellType::FLOAT),
                                            std::move(generator),
                                            HnswIndexConfig(5, 2, 10, 0, heuristic_select_neighbors));
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
        index->assign_generation(gen_handler.getCurrentGeneration());
        gen_handler.incGeneration();
        index->reclaim_memory(gen_handler.get_oldest_used_generation());
    }
    void set_filter(std::vector<uint32_t> docids) {
        uint32_t sz = 10;
        global_filter = GlobalFilter::create(docids, sz);
    }
    GenerationHandler::Guard take_read_guard() {
        return gen_handler.takeGuard();
    }
    MemoryUsage memory_usage() const {
        return index->memory_usage();
    }
    MemoryUsage commit_and_update_stat() {
        commit();
        CompactionStrategy compaction_strategy;
        return index->update_stat(compaction_strategy);
    }
    void expect_entry_point(uint32_t exp_nodeid, uint32_t exp_level) {
        EXPECT_EQ(exp_nodeid, index->get_entry_nodeid());
        EXPECT_EQ(exp_level, index->get_entry_level());
    }
    void expect_level_0(uint32_t nodeid, const HnswTestNode::LinkArray& exp_links) {
        auto node = index->get_node(nodeid);
        ASSERT_EQ(1, node.size());
        EXPECT_EQ(exp_links, node.level(0));
    }
    void expect_empty_level_0(uint32_t nodeid) {
        auto node = index->get_node(nodeid);
        EXPECT_TRUE(node.empty());
    }
    void expect_levels(uint32_t nodeid, const HnswTestNode::LevelArray& exp_levels) {
        auto act_node = index->get_node(nodeid);
        ASSERT_EQ(exp_levels.size(), act_node.size());
        EXPECT_EQ(exp_levels, act_node.levels());
    }
    void expect_top_3(uint32_t docid, std::vector<uint32_t> exp_hits) {
        uint32_t k = 3;
        auto qv = vectors.get_vector(docid, 0);
        auto rv = index->top_k_candidates(qv, k, global_filter->ptr_if_active()).peek();
        std::sort(rv.begin(), rv.end(), LesserDistance());
        size_t idx = 0;
        for (const auto & hit : rv) {
            if (idx < exp_hits.size()) {
                EXPECT_EQ(index->get_docid(hit.nodeid), exp_hits[idx++]);
            }
        }
        if (exp_hits.size() == k) {
            std::vector<uint32_t> expected_by_docid = exp_hits;
            std::sort(expected_by_docid.begin(), expected_by_docid.end());
            auto got_by_docid = index->find_top_k(k, qv, k, 100100.25);
            for (idx = 0; idx < k; ++idx) {
                EXPECT_EQ(expected_by_docid[idx], got_by_docid[idx].docid);
            }
        }
        check_with_distance_threshold(docid);
    }
    void check_with_distance_threshold(uint32_t docid) {
        auto qv = vectors.get_vector(docid, 0);
        uint32_t k = 3;
        auto rv = index->top_k_candidates(qv, k, global_filter->ptr_if_active()).peek();
        std::sort(rv.begin(), rv.end(), LesserDistance());
        EXPECT_EQ(rv.size(), 3);
        EXPECT_LE(rv[0].distance, rv[1].distance);
        double thr = (rv[0].distance + rv[1].distance) * 0.5;
        auto got_by_docid = (global_filter->is_active())
            ? index->find_top_k_with_filter(k, qv, *global_filter, k, thr)
            : index->find_top_k(k, qv, k, thr);
        EXPECT_EQ(got_by_docid.size(), 1);
        EXPECT_EQ(got_by_docid[0].docid, index->get_docid(rv[0].nodeid));
        for (const auto & hit : got_by_docid) {
            LOG(debug, "from docid=%u found docid=%u dist=%g (threshold %g)\n",
                docid, hit.docid, hit.distance, thr);
        }
    }

    FloatVectors& get_vectors() { return vectors; }
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

    set_filter({2,3,4,6});
    expect_top_3(2, {2, 3});
    expect_top_3(4, {4, 3});
    expect_top_3(5, {6, 2});
    expect_top_3(6, {6, 2});
    expect_top_3(7, {3, 2});
    expect_top_3(8, {4, 3});
    expect_top_3(9, {3, 2});
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
    // Doc 3 is closest to 1 and they are linked.
    // Doc 3 is NOT linked to 2, since that is closer to 1 also.
    expect_level_0(1, {2, 3});
    expect_level_0(2, {1});
    expect_levels(3, {{1}, {}});

    // Doc 4 is closest to 1 and they are linked.
    // Doc 4 is NOT linked to 3 as the distance between 4 and 3 is greater than the distance between 3 and 1.
    // Doc 3 is therefore reachable via 1. Same argument for why doc 4 is not linked to 2.
    add_document(4);
    expect_entry_point(3, 1);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1});
    expect_levels(3, {{1}, {}});
    expect_level_0(4, {1});

    // Doc 5 is closest to 2 and they are linked.
    // The other docs are reachable via 2, and no other links are created. Same argument as with doc 4 above.
    add_document(5);
    expect_entry_point(3, 1);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 5});
    expect_levels(3, {{1}, {}});
    expect_level_0(4, {1});
    expect_level_0(5, {2});

    // Doc 6 is closest to 5 and they are linked.
    // Doc 6 is also linked to 2 as the distance between 6 and 2 is less than the distance between 2 and 5.
    // Doc 6 is also added to level 1 and 2, and linked to doc 3 in level 1.
    add_document(6, 2);
    expect_entry_point(6, 2);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 5, 6});
    expect_levels(3, {{1}, {6}});
    expect_level_0(4, {1});
    expect_level_0(5, {2, 6});
    expect_levels(6, {{2, 5}, {3}, {}});

    // Doc 7 is closest to 3 and they are linked.
    // Doc 7 is also linked to 6 as the distance between 7 and 6 is less than the distance between 6 and 3.
    // Docs 1, 2, 4 are reachable via 3.
    add_document(7);
    expect_entry_point(6, 2);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 5, 6});
    expect_levels(3, {{1, 7}, {6}});
    expect_level_0(4, {1});
    expect_level_0(5, {2, 6});
    expect_levels(6, {{2, 5, 7}, {3}, {}});
    expect_level_0(7, {3, 6});
    {
        Slime actualSlime;
        SlimeInserter inserter(actualSlime);
        index->get_state(inserter);
        const auto &root = actualSlime.get();
        EXPECT_EQ(0, root["memory_usage"]["onHold"].asLong());
        EXPECT_EQ(8, root["nodes"].asLong());
        EXPECT_EQ(7, root["valid_nodes"].asLong());
        EXPECT_EQ(0, root["level_histogram"][0].asLong());
        EXPECT_EQ(5, root["level_histogram"][1].asLong());
        EXPECT_EQ(0, root["level_0_links_histogram"][0].asLong());
        EXPECT_EQ(1, root["level_0_links_histogram"][1].asLong());
        EXPECT_EQ(3, root["level_0_links_histogram"][2].asLong());
        EXPECT_EQ(3, root["level_0_links_histogram"][3].asLong());
        EXPECT_EQ(0, root["level_0_links_histogram"][4].asLong());
        EXPECT_EQ(0, root["unreachable_nodes"].asLong());
    }

    // removing 1, its neighbors {2,3,4} will try to
    // link together, but since 2 already has enough links
    // only 3 and 4 will become neighbors:
    remove_document(1);
    expect_entry_point(6, 2);
    expect_level_0(2, {5, 6});
    expect_levels(3, {{4, 7}, {6}});
    expect_level_0(4, {3});
    expect_level_0(5, {2, 6});
    expect_levels(6, {{2, 5, 7}, {3}, {}});
    expect_level_0(7, {3, 6});
    {
        Slime actualSlime;
        SlimeInserter inserter(actualSlime);
        index->get_state(inserter);
        const auto &root = actualSlime.get();
        EXPECT_EQ(0, root["memory_usage"]["onHold"].asLong());
        EXPECT_EQ(8, root["nodes"].asLong());
        EXPECT_EQ(6, root["valid_nodes"].asLong());
        EXPECT_EQ(0, root["level_histogram"][0].asLong());
        EXPECT_EQ(4, root["level_histogram"][1].asLong());
        EXPECT_EQ(0, root["level_0_links_histogram"][0].asLong());
        EXPECT_EQ(1, root["level_0_links_histogram"][1].asLong());
        EXPECT_EQ(4, root["level_0_links_histogram"][2].asLong());
        EXPECT_EQ(1, root["level_0_links_histogram"][3].asLong());
        EXPECT_EQ(0, root["level_0_links_histogram"][4].asLong());
        EXPECT_EQ(0, root["unreachable_nodes"].asLong());
    }
}

TEST_F(HnswIndexTest, manual_insert)
{
    init(false);

    std::vector<uint32_t> nbl;
    HnswTestNode empty{nbl};
    index->set_node(1, empty);
    index->set_node(2, empty);

    HnswTestNode three{{1,2}};
    index->set_node(3, three);
    expect_level_0(1, {3});
    expect_level_0(2, {3});
    expect_level_0(3, {1,2});

    expect_entry_point(1, 0);

    HnswTestNode twolevels{{{1},nbl}};
    index->set_node(4, twolevels);

    expect_entry_point(4, 1);
    expect_level_0(1, {3,4});

    HnswTestNode five{{{1,2}, {4}}};
    index->set_node(5, five);

    expect_levels(1, {{3,4,5}});
    expect_levels(2, {{3,5}});
    expect_levels(3, {{1,2}});
    expect_levels(4, {{1}, {5}});
    expect_levels(5, {{1,2}, {4}});
}

TEST_F(HnswIndexTest, memory_is_reclaimed_when_doing_changes_to_graph)
{
    init(false);

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
    HnswTestNode empty{nbl};
    index->set_node(1, empty);
    nbl.push_back(1);
    HnswTestNode nb1{nbl};
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
    HnswTestNode empty{nbl};
    index->set_node(1, empty);
    nbl.push_back(1);
    HnswTestNode nb1{nbl};
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

namespace {

template <class ResultGraph>
ResultGraph
make_graph_helper(HnswIndex& index)
{
    using LevelArrayRef = HnswGraph::LevelArrayRef;
    using LinkArrayRef = HnswGraph::LinkArrayRef;
    auto& graph = index.get_graph();
    ResultGraph result(graph.size());
    assert(!graph.get_node_ref(0).valid());
    for (uint32_t doc_id = 1; doc_id < graph.size(); ++doc_id) {
        auto& node = result[doc_id];
        auto node_ref = graph.get_node_ref(doc_id);
        if constexpr (std::is_same_v<std::remove_reference_t<decltype(node)>, uint32_t>) {
            node = node_ref.ref();
        } else {
            LevelArrayRef level_array(graph.get_level_array(node_ref));
            for (uint32_t level = 0; level < level_array.size(); ++level) {
                if constexpr (std::is_same_v<std::remove_reference_t<decltype(node)>, std::vector<uint32_t>>) {
                    node.emplace_back(level_array[level].load_relaxed().ref());
                } else {
                    LinkArrayRef link_array(graph.get_link_array(level_array, level));
                    node.emplace_back(std::vector<uint32_t>(link_array.begin(), link_array.end()));
                }
            }
        }
    }
    return result;
}

using LinkGraph = std::vector<std::vector<std::vector<uint32_t>>>;

LinkGraph
make_link_graph(HnswIndex& index)
{
    return make_graph_helper<LinkGraph>(index);
}

using LinkArrayRefGraph = std::vector<std::vector<uint32_t>>;

LinkArrayRefGraph
make_link_array_refs(HnswIndex& index)
{
    return make_graph_helper<LinkArrayRefGraph>(index);
}

using LevelArrayRefGraph = std::vector<uint32_t>;

LevelArrayRefGraph
make_level_array_refs(HnswIndex& index)
{
    return make_graph_helper<LevelArrayRefGraph>(index);
}

}

TEST_F(HnswIndexTest, hnsw_graph_is_compacted)
{
    init(true);
    get_vectors().clear();
    uint32_t doc_id = 1;
    for (uint32_t x = 0; x < 100; ++x) {
        for (uint32_t y = 0; y < 50; ++y) {
            get_vectors().set(doc_id, { float(x), float(y) });
            ++doc_id;
        }
    }
    uint32_t doc_id_end = doc_id;
    for (doc_id = 1; doc_id < doc_id_end; ++doc_id) {
        add_document(doc_id);
    }
    for (doc_id = 10; doc_id < doc_id_end; ++doc_id) {
        remove_document(doc_id);
    }
    auto mem_1 = commit_and_update_stat();
    auto link_graph_1 = make_link_graph(*index);
    auto link_array_refs_1 = make_link_array_refs(*index);
    auto level_array_refs_1 = make_level_array_refs(*index);
    // Normal compaction
    EXPECT_TRUE(index->consider_compact(CompactionStrategy()));
    auto mem_2 = commit_and_update_stat();
    EXPECT_LT(mem_2.usedBytes(), mem_1.usedBytes());
    for (uint32_t i = 0; i < 10; ++i) {
        mem_1 = mem_2;
        // Forced compaction to move things around
        CompactionSpec compaction_spec(true, false);
        CompactionStrategy compaction_strategy;
        index->compact_link_arrays(compaction_spec, compaction_strategy);
        index->compact_level_arrays(compaction_spec, compaction_strategy);
        commit();
        index->update_stat(compaction_strategy);
        mem_2 = commit_and_update_stat();
        EXPECT_LE(mem_2.usedBytes(), mem_1.usedBytes());
        if (mem_2.usedBytes() == mem_1.usedBytes()) {
            break;
        }
    }
    auto link_graph_2 = make_link_graph(*index);
    auto link_array_refs_2 = make_link_array_refs(*index);
    auto level_array_refs_2 = make_level_array_refs(*index);
    EXPECT_EQ(link_graph_1, link_graph_2);
    EXPECT_NE(link_array_refs_1, link_array_refs_2);
    EXPECT_NE(level_array_refs_1, level_array_refs_2);
    index->shrink_lid_space(10);
    auto mem_3 = commit_and_update_stat();
    EXPECT_LT(mem_3.usedBytes(), mem_2.usedBytes());
}

TEST(LevelGeneratorTest, gives_various_levels)
{
    InvLogLevelGenerator generator(4);
    std::vector<uint32_t> got_levels(16);
    for (auto & v : got_levels) { v = generator.max_level(); }
    EXPECT_EQ(got_levels, std::vector<uint32_t>({
        2, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0
    }));
    for (auto & v : got_levels) { v = generator.max_level(); }
    EXPECT_EQ(got_levels, std::vector<uint32_t>({
        0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    }));
    for (auto & v : got_levels) { v = generator.max_level(); }
    EXPECT_EQ(got_levels, std::vector<uint32_t>({
        0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 2, 0
    }));
    for (auto & v : got_levels) { v = generator.max_level(); }
    EXPECT_EQ(got_levels, std::vector<uint32_t>({
        0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1
    }));
    for (auto & v : got_levels) { v = generator.max_level(); }
    EXPECT_EQ(got_levels, std::vector<uint32_t>({
        0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 2
    }));
    for (auto & v : got_levels) { v = generator.max_level(); }
    EXPECT_EQ(got_levels, std::vector<uint32_t>({
        0, 1, 1, 0, 3, 1, 2, 0, 0, 1, 0, 0, 0, 0, 0, 0
    }));

    uint32_t left = 1000000;
    std::vector<uint32_t> hist;
    for (uint32_t i = 0; i < left; ++i) {
        uint32_t l = generator.max_level();
        if (hist.size() <= l) {
            hist.resize(l+1);
        }
        hist[l]++;
    }
    for (uint32_t l = 0; l < hist.size(); ++l) {
        double expected = left * 0.75;
        EXPECT_TRUE(hist[l] < expected*1.01 + 100);
        EXPECT_TRUE(hist[l] > expected*0.99 - 100);
        left *= 0.25;
    }
    EXPECT_TRUE(hist.size() < 14);
}

class TwoPhaseTest : public HnswIndexTest {
public:
    TwoPhaseTest() : HnswIndexTest() {
        init(true);
        vectors.set(4, {1, 3}).set(5, {13, 3}).set(6, {7, 13})
               .set(1, {3, 7}).set(2, {7, 1}).set(3, {11, 7})
               .set(7, {6, 5}).set(8, {5, 5}).set(9, {6, 6});
    }
    using UP = std::unique_ptr<PrepareResult>;
    UP prepare_add(uint32_t docid, uint32_t max_level = 0) {
        level_generator->level = max_level;
        vespalib::GenerationHandler::Guard dummy;
        auto vectors_to_add = vectors.get_vectors(docid);
        return index->prepare_add_document(docid, vectors_to_add, dummy);
    }
    void complete_add(uint32_t docid, UP up) {
        index->complete_add_document(docid, std::move(up));
        commit();
    }
};

TEST_F(TwoPhaseTest, two_phase_add)
{
    add_document(1);
    add_document(2);
    add_document(3);
    expect_entry_point(1, 0);
    add_document(4, 1);
    add_document(5, 1);
    add_document(6, 2);
    expect_entry_point(6, 2);

    expect_level_0(1, {2,4,6});
    expect_level_0(2, {1,3,4,5});
    expect_level_0(3, {2,5,6});

    expect_levels(4, {{1,2}, {5,6}});
    expect_levels(5, {{2,3}, {4,6}});
    expect_levels(6, {{1,3}, {4,5}, {}});

    auto up = prepare_add(7, 1);
    // simulate things happening while 7 is in progress:
    add_document(8); // added
    remove_document(1); // removed
    remove_document(5);
    vectors.set(5, {8, 14}); // updated and moved
    add_document(5, 2);
    add_document(9, 1); // added
    complete_add(7, std::move(up));

    // 1 filtered out because it was removed
    // 5 filtered out because it was updated
    expect_levels(7, {{2}, {4}});
}


GTEST_MAIN_RUN_ALL_TESTS()
