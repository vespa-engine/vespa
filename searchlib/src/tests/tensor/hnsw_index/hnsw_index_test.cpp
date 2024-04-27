// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/test/vector_buffer_reader.h>
#include <vespa/searchlib/test/vector_buffer_writer.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/doc_vector_access.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/searchlib/tensor/hnsw_index_loader.hpp>
#include <vespa/searchlib/tensor/hnsw_index_saver.h>
#include <vespa/searchlib/tensor/random_level_generator.h>
#include <vespa/searchlib/tensor/inv_log_level_generator.h>
#include <vespa/searchlib/tensor/subspace_type.h>
#include <vespa/searchlib/tensor/empty_subspace.h>
#include <vespa/searchlib/tensor/vector_bundle.h>
#include <vespa/searchlib/queryeval/global_filter.h>
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/fake_doom.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <type_traits>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("hnsw_index_test");

using vespalib::GenerationHandler;
using vespalib::MemoryUsage;
using namespace search::tensor;
using namespace vespalib::slime;
using vespalib::Slime;
using search::BitVector;
using search::BufferWriter;
using vespalib::eval::get_cell_type;
using vespalib::eval::ValueType;
using vespalib::datastore::CompactionSpec;
using vespalib::datastore::CompactionStrategy;
using search::queryeval::GlobalFilter;
using search::test::VectorBufferReader;
using search::test::VectorBufferWriter;

template <typename FloatType>
class MyDocVectorAccess : public DocVectorAccess {
private:
    using Vector = std::vector<FloatType>;
    using ArrayRef = vespalib::ConstArrayRef<FloatType>;
    mutable std::vector<Vector> _vectors;
    SubspaceType                _subspace_type;
    EmptySubspace               _empty;
    mutable uint32_t            _get_vector_count;
    mutable uint32_t            _schedule_clear_tensor;
    mutable uint32_t            _cleared_tensor_docid;

public:
    MyDocVectorAccess()
        : _vectors(),
          _subspace_type(ValueType::make_type(get_cell_type<FloatType>(), {{"dims", 2}})),
          _empty(_subspace_type),
          _get_vector_count(0),
          _schedule_clear_tensor(0),
          _cleared_tensor_docid(0)
    {
    }
    MyDocVectorAccess& set(uint32_t docid, const Vector& vec) {
        if (docid >= _vectors.size()) {
            _vectors.resize(docid + 1);
        }
        _vectors[docid] = vec;
        return *this;
    }
    void clear(uint32_t docid) const {
        if (docid < _vectors.size()) {
            _vectors[docid].clear();
        }
    }
    vespalib::eval::TypedCells get_vector(uint32_t docid, uint32_t subspace) const noexcept override {
        ++_get_vector_count;
        if (_schedule_clear_tensor != 0) {
            if (--_schedule_clear_tensor == 0) {
                // Simulate race where writer thread has cleared a tensor.
                clear(docid);
                _cleared_tensor_docid = docid;
            }
        }
        auto bundle = get_vectors(docid);
        if (subspace < bundle.subspaces()) {
            return bundle.cells(subspace);
        }
        return _empty.cells();
    }
    VectorBundle get_vectors(uint32_t docid) const noexcept override {
        ArrayRef ref(_vectors[docid]);
        assert((ref.size() % _subspace_type.size()) == 0);
        uint32_t subspaces = ref.size() / _subspace_type.size();
        return {ref.data(), subspaces, _subspace_type};
    }

    void clear() { _vectors.clear(); }

    uint32_t get_vector_count() const noexcept { return _get_vector_count; }
    void clear_cleared_tensor_docid() { _cleared_tensor_docid = 0; }
    uint32_t get_cleared_tensor_docid() const noexcept { return _cleared_tensor_docid; }
    void set_schedule_clear_tensor(uint32_t v) { _schedule_clear_tensor = v; }
};

class MyBoundDistanceFunction : public BoundDistanceFunction {
    std::unique_ptr<BoundDistanceFunction> _real;

public:
    MyBoundDistanceFunction(std::unique_ptr<BoundDistanceFunction> real)
        : _real(std::move(real))
    {
    }

    ~MyBoundDistanceFunction() override;

    double convert_threshold(double threshold) const noexcept override {
        return _real->convert_threshold(threshold);
    }
    double to_rawscore(double distance) const noexcept override {
        return _real->to_rawscore(distance);
    }
    double to_distance(double rawscore) const noexcept override {
        return _real->to_distance(rawscore);
    }

    double min_rawscore() const noexcept override { return _real->min_rawscore(); }

    double calc(TypedCells rhs) const noexcept override {
        EXPECT_FALSE(rhs.non_existing_attribute_value());
        return _real->calc(rhs);
    }

    double calc_with_limit(TypedCells rhs, double limit) const noexcept override {
        EXPECT_FALSE(rhs.non_existing_attribute_value());
        return _real->calc_with_limit(rhs, limit);
    }
};

MyBoundDistanceFunction::~MyBoundDistanceFunction() = default;

class MyDistanceFunctionFactory : public DistanceFunctionFactory
{
    std::unique_ptr<DistanceFunctionFactory> _real;
public:
    MyDistanceFunctionFactory(std::unique_ptr<DistanceFunctionFactory> real)
        : _real(std::move(real))
    {
    }

    ~MyDistanceFunctionFactory() override;

    std::unique_ptr<BoundDistanceFunction> for_query_vector(TypedCells lhs) override {
        EXPECT_FALSE(lhs.non_existing_attribute_value());
        return std::make_unique<MyBoundDistanceFunction>(_real->for_query_vector(lhs));
    }

    std::unique_ptr<BoundDistanceFunction> for_insertion_vector(TypedCells lhs) override {
        EXPECT_FALSE(lhs.non_existing_attribute_value());
        return std::make_unique<MyBoundDistanceFunction>(_real->for_insertion_vector(lhs));
    }
};

MyDistanceFunctionFactory::~MyDistanceFunctionFactory() = default;

struct LevelGenerator : public RandomLevelGenerator {
    uint32_t level;
    LevelGenerator() : level(0) {}
    uint32_t max_level() override { return level; }
};

using FloatVectors = MyDocVectorAccess<float>;

template <typename IndexType>
class HnswIndexTest : public ::testing::Test {
public:
    FloatVectors vectors;
    std::shared_ptr<GlobalFilter> global_filter;
    LevelGenerator* level_generator;
    GenerationHandler gen_handler;
    std::unique_ptr<IndexType> index;
    std::unique_ptr<vespalib::FakeDoom> _doom;

    HnswIndexTest()
        : vectors(),
          global_filter(GlobalFilter::create()),
          level_generator(),
          gen_handler(),
          index(),
          _doom(std::make_unique<vespalib::FakeDoom>())
    {
        vectors.set(1, {2, 2}).set(2, {3, 2}).set(3, {2, 3})
               .set(4, {1, 2}).set(5, {8, 3}).set(6, {7, 2})
               .set(7, {3, 5}).set(8, {0, 3}).set(9, {4, 5});
    }

    ~HnswIndexTest() override;

    auto dff_real() {
        return search::tensor::make_distance_function_factory(
                search::attribute::DistanceMetric::Euclidean,
                vespalib::eval::CellType::FLOAT);
    }

    auto dff() {
        return std::make_unique<MyDistanceFunctionFactory>(dff_real());
    }

    void init(bool heuristic_select_neighbors) {
        auto generator = std::make_unique<LevelGenerator>();
        level_generator = generator.get();
        index = std::make_unique<IndexType>(vectors, dff(),
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
    void set_filter(const std::vector<uint32_t>& docids) {
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
    void expect_top_3_by_docid(const vespalib::string& label, std::vector<float> qv, const std::vector<uint32_t>& exp) {
        SCOPED_TRACE(label);
        uint32_t k = 3;
        uint32_t explore_k = 100;
        vespalib::ArrayRef qv_ref(qv);
        vespalib::eval::TypedCells qv_cells(qv_ref);
        auto df = index->distance_function_factory().for_query_vector(qv_cells);
        auto got_by_docid = (global_filter->is_active()) ?
                            index->find_top_k_with_filter(k, *df, *global_filter, explore_k, _doom->get_doom(), 10000.0) :
                            index->find_top_k(k, *df, explore_k, _doom->get_doom(), 10000.0);
        std::vector<uint32_t> act;
        act.reserve(got_by_docid.size());
        for (auto& hit : got_by_docid) {
            act.emplace_back(hit.docid);
        }
        EXPECT_EQ(exp, act);
    }
    void expect_top_3(uint32_t docid, std::vector<uint32_t> exp_hits) {
        uint32_t k = 3;
        auto qv = vectors.get_vector(docid, 0);
        auto df = index->distance_function_factory().for_query_vector(qv);
        auto rv = index->top_k_candidates(*df, k, global_filter->ptr_if_active(), _doom->get_doom()).peek();
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
            auto got_by_docid = index->find_top_k(k, *df, k, _doom->get_doom(), 100100.25);
            for (idx = 0; idx < k; ++idx) {
                EXPECT_EQ(expected_by_docid[idx], got_by_docid[idx].docid);
            }
        }
        if (!exp_hits.empty()) {
            check_with_distance_threshold(docid);
        }
    }
    void check_with_distance_threshold(uint32_t docid) {
        auto qv = vectors.get_vector(docid, 0);
        auto df = index->distance_function_factory().for_query_vector(qv);
        uint32_t k = 3;
        auto rv = index->top_k_candidates(*df, k, global_filter->ptr_if_active(), _doom->get_doom()).peek();
        std::sort(rv.begin(), rv.end(), LesserDistance());
        EXPECT_EQ(rv.size(), 3);
        EXPECT_LE(rv[0].distance, rv[1].distance);
        double thr = (rv[0].distance + rv[1].distance) * 0.5;
        auto got_by_docid = (global_filter->is_active())
            ? index->find_top_k_with_filter(k, *df, *global_filter, k, _doom->get_doom(), thr)
            : index->find_top_k(k, *df, k, _doom->get_doom(), thr);
        EXPECT_EQ(got_by_docid.size(), 1);
        EXPECT_EQ(got_by_docid[0].docid, index->get_docid(rv[0].nodeid));
        for (const auto & hit : got_by_docid) {
            LOG(debug, "from docid=%u found docid=%u dist=%g (threshold %g)\n",
                docid, hit.docid, hit.distance, thr);
        }
    }

    FloatVectors& get_vectors() { return vectors; }

    uint32_t get_single_nodeid(uint32_t docid) {
        auto& id_mapping = index->get_id_mapping();
        auto nodeids = id_mapping.get_ids(docid);
        EXPECT_EQ(1, nodeids.size());
        return nodeids[0];
    }

    void make_savetest_index()
    {
        this->add_document(7);
        this->add_document(4);
    }

    void check_savetest_index(const vespalib::string& label) {
        SCOPED_TRACE(label);
        auto nodeid_for_doc_7 = get_single_nodeid(7);
        auto nodeid_for_doc_4 = get_single_nodeid(4);
        EXPECT_EQ(is_single ? 7 : 1, nodeid_for_doc_7);
        EXPECT_EQ(is_single ? 4 : 2, nodeid_for_doc_4);
        this->expect_level_0(nodeid_for_doc_7, { nodeid_for_doc_4 });
        this->expect_level_0(nodeid_for_doc_4, { nodeid_for_doc_7 });
    }

    std::vector<char> save_index() const {
        HnswIndexSaver saver(index->get_graph());
        VectorBufferWriter vector_writer;
        saver.save(vector_writer);
        return vector_writer.output;
    }

    void load_index(std::vector<char> data) {
        auto& graph = index->get_graph();
        auto& id_mapping = index->get_id_mapping();
        HnswIndexLoader<VectorBufferReader, IndexType::index_type> loader(graph, id_mapping, std::make_unique<VectorBufferReader>(data));
        while (loader.load_next()) {}
    }
    void reset_doom() {
        _doom = std::make_unique<vespalib::FakeDoom>();
    }
    void reset_doom(vespalib::steady_time::duration time_to_doom) {
        _doom = std::make_unique<vespalib::FakeDoom>(time_to_doom);
    }
    uint32_t get_active_nodes() const noexcept {
        return index->get_active_nodes();
    }

    /*
     * Simulate race where writer has cleared a tensor while read thread still
     * use old graph.
     */
    void writer_clears_tensor(uint32_t docid) { vectors.clear(docid); }

    uint32_t get_vector_count() const noexcept { return vectors.get_vector_count(); }
    void clear_cleared_tensor_docid() { vectors.clear_cleared_tensor_docid(); }
    uint32_t get_cleared_tensor_docid() const noexcept { return vectors.get_cleared_tensor_docid(); }
    void set_schedule_clear_tensor(uint32_t v) { vectors.set_schedule_clear_tensor(v); }

    static constexpr bool is_single = std::is_same_v<IndexType, HnswIndex<HnswIndexType::SINGLE>>;
};

template <typename IndexType>
HnswIndexTest<IndexType>::~HnswIndexTest() = default;

using HnswIndexTestTypes = ::testing::Types<HnswIndex<HnswIndexType::SINGLE>, HnswIndex<HnswIndexType::MULTI>>;

TYPED_TEST_SUITE(HnswIndexTest, HnswIndexTestTypes);

TYPED_TEST(HnswIndexTest, 2d_vectors_inserted_in_level_0_graph_with_simple_select_neighbors)
{
    this->init(false);
    EXPECT_EQ(0, this->get_active_nodes());

    this->add_document(1);
    this->expect_level_0(1, {});
    EXPECT_EQ(1, this->get_active_nodes());

    this->add_document(2);
    this->expect_level_0(1, {2});
    this->expect_level_0(2, {1});
    EXPECT_EQ(2, this->get_active_nodes());

    this->add_document(3);
    this->expect_level_0(1, {2, 3});
    this->expect_level_0(2, {1, 3});
    this->expect_level_0(3, {1, 2});
    EXPECT_EQ(3, this->get_active_nodes());

    this->add_document(4);
    this->expect_level_0(1, {2, 3, 4});
    this->expect_level_0(2, {1, 3});
    this->expect_level_0(3, {1, 2, 4});
    this->expect_level_0(4, {1, 3});
    EXPECT_EQ(4, this->get_active_nodes());

    this->add_document(5);
    this->expect_level_0(1, {2, 3, 4});
    this->expect_level_0(2, {1, 3, 5});
    this->expect_level_0(3, {1, 2, 4, 5});
    this->expect_level_0(4, {1, 3});
    this->expect_level_0(5, {2, 3});
    EXPECT_EQ(5, this->get_active_nodes());

    this->add_document(6);
    this->expect_level_0(1, {2, 3, 4});
    this->expect_level_0(2, {1, 3, 5, 6});
    this->expect_level_0(3, {1, 2, 4, 5});
    this->expect_level_0(4, {1, 3});
    this->expect_level_0(5, {2, 3, 6});
    this->expect_level_0(6, {2, 5});
    EXPECT_EQ(6, this->get_active_nodes());

    this->add_document(7);
    this->expect_level_0(1, {2, 3, 4});
    this->expect_level_0(2, {1, 3, 5, 6, 7});
    this->expect_level_0(3, {1, 2, 4, 5, 7});
    this->expect_level_0(4, {1, 3});
    this->expect_level_0(5, {2, 3, 6});
    this->expect_level_0(6, {2, 5});
    this->expect_level_0(7, {2, 3});
    EXPECT_EQ(7, this->get_active_nodes());

    this->expect_top_3(1, {1});
    this->expect_top_3(2, {2, 1, 3});
    this->expect_top_3(3, {3});
    this->expect_top_3(4, {4, 1, 3});
    this->expect_top_3(5, {5, 6, 2});
    this->expect_top_3(6, {6, 5, 2});
    this->expect_top_3(7, {7, 3, 2});
    this->expect_top_3(8, {4, 3, 1});
    this->expect_top_3(9, {7, 3, 2});

    this->set_filter({2,3,4,6});
    this->expect_top_3(2, {2, 3});
    this->expect_top_3(4, {4, 3});
    this->expect_top_3(5, {6, 2});
    this->expect_top_3(6, {6, 2});
    this->expect_top_3(7, {3, 2});
    this->expect_top_3(8, {4, 3});
    this->expect_top_3(9, {3, 2});
    this->reset_doom(-1s);
    this->expect_top_3(2, {});
}

TYPED_TEST(HnswIndexTest, 2d_vectors_inserted_and_removed)
{
    this->init(false);
    EXPECT_EQ(0, this->get_active_nodes());

    this->add_document(1);
    this->expect_level_0(1, {});
    this->expect_entry_point(1, 0);
    EXPECT_EQ(1, this->get_active_nodes());

    this->add_document(2);
    this->expect_level_0(1, {2});
    this->expect_level_0(2, {1});
    this->expect_entry_point(1, 0);
    EXPECT_EQ(2, this->get_active_nodes());

    this->add_document(3);
    this->expect_level_0(1, {2, 3});
    this->expect_level_0(2, {1, 3});
    this->expect_level_0(3, {1, 2});
    this->expect_entry_point(1, 0);
    EXPECT_EQ(3, this->get_active_nodes());

    this->remove_document(2);
    this->expect_level_0(1, {3});
    this->expect_level_0(3, {1});
    this->expect_entry_point(1, 0);
    EXPECT_EQ(2, this->get_active_nodes());

    this->remove_document(1);
    this->expect_level_0(3, {});
    this->expect_entry_point(3, 0);
    EXPECT_EQ(1, this->get_active_nodes());

    this->remove_document(3);
    this->expect_entry_point(0, -1);
    EXPECT_EQ(0, this->get_active_nodes());
}

TYPED_TEST(HnswIndexTest, 2d_vectors_inserted_in_hierarchic_graph_with_heuristic_select_neighbors)
{
    this->init(true);
    EXPECT_EQ(0, this->get_active_nodes());

    this->add_document(1);
    this->expect_entry_point(1, 0);
    this->expect_level_0(1, {});
    EXPECT_EQ(1, this->get_active_nodes());

    this->add_document(2);
    this->expect_entry_point(1, 0);
    this->expect_level_0(1, {2});
    this->expect_level_0(2, {1});
    EXPECT_EQ(2, this->get_active_nodes());

    // Doc 3 is also added to level 1
    this->add_document(3, 1);
    this->expect_entry_point(3, 1);
    // Doc 3 is closest to 1 and they are linked.
    // Doc 3 is NOT linked to 2, since that is closer to 1 also.
    this->expect_level_0(1, {2, 3});
    this->expect_level_0(2, {1});
    this->expect_levels(3, {{1}, {}});
    EXPECT_EQ(3, this->get_active_nodes());

    // Doc 4 is closest to 1 and they are linked.
    // Doc 4 is NOT linked to 3 as the distance between 4 and 3 is greater than the distance between 3 and 1.
    // Doc 3 is therefore reachable via 1. Same argument for why doc 4 is not linked to 2.
    this->add_document(4);
    this->expect_entry_point(3, 1);
    this->expect_level_0(1, {2, 3, 4});
    this->expect_level_0(2, {1});
    this->expect_levels(3, {{1}, {}});
    this->expect_level_0(4, {1});
    EXPECT_EQ(4, this->get_active_nodes());

    // Doc 5 is closest to 2 and they are linked.
    // The other docs are reachable via 2, and no other links are created. Same argument as with doc 4 above.
    this->add_document(5);
    this->expect_entry_point(3, 1);
    this->expect_level_0(1, {2, 3, 4});
    this->expect_level_0(2, {1, 5});
    this->expect_levels(3, {{1}, {}});
    this->expect_level_0(4, {1});
    this->expect_level_0(5, {2});
    EXPECT_EQ(5, this->get_active_nodes());

    // Doc 6 is closest to 5 and they are linked.
    // Doc 6 is also linked to 2 as the distance between 6 and 2 is less than the distance between 2 and 5.
    // Doc 6 is also added to level 1 and 2, and linked to doc 3 in level 1.
    this->add_document(6, 2);
    this->expect_entry_point(6, 2);
    this->expect_level_0(1, {2, 3, 4});
    this->expect_level_0(2, {1, 5, 6});
    this->expect_levels(3, {{1}, {6}});
    this->expect_level_0(4, {1});
    this->expect_level_0(5, {2, 6});
    this->expect_levels(6, {{2, 5}, {3}, {}});
    EXPECT_EQ(6, this->get_active_nodes());

    // Doc 7 is closest to 3 and they are linked.
    // Doc 7 is also linked to 6 as the distance between 7 and 6 is less than the distance between 6 and 3.
    // Docs 1, 2, 4 are reachable via 3.
    this->add_document(7);
    this->expect_entry_point(6, 2);
    this->expect_level_0(1, {2, 3, 4});
    this->expect_level_0(2, {1, 5, 6});
    this->expect_levels(3, {{1, 7}, {6}});
    this->expect_level_0(4, {1});
    this->expect_level_0(5, {2, 6});
    this->expect_levels(6, {{2, 5, 7}, {3}, {}});
    this->expect_level_0(7, {3, 6});
    EXPECT_EQ(7, this->get_active_nodes());
    {
        Slime actualSlime;
        SlimeInserter inserter(actualSlime);
        this->index->get_state(inserter);
        const auto &root = actualSlime.get();
        EXPECT_EQ(0, root["memory_usage"]["onHold"].asLong());
        EXPECT_EQ(8, root["nodeid_limit"].asLong());
        EXPECT_EQ(7, root["nodes"].asLong());
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
    this->remove_document(1);
    this->expect_entry_point(6, 2);
    this->expect_level_0(2, {5, 6});
    this->expect_levels(3, {{4, 7}, {6}});
    this->expect_level_0(4, {3});
    this->expect_level_0(5, {2, 6});
    this->expect_levels(6, {{2, 5, 7}, {3}, {}});
    this->expect_level_0(7, {3, 6});
    EXPECT_EQ(6, this->get_active_nodes());
    {
        Slime actualSlime;
        SlimeInserter inserter(actualSlime);
        this->index->get_state(inserter);
        const auto &root = actualSlime.get();
        EXPECT_EQ(0, root["memory_usage"]["onHold"].asLong());
        EXPECT_EQ(8, root["nodeid_limit"].asLong());
        EXPECT_EQ(6, root["nodes"].asLong());
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

TYPED_TEST(HnswIndexTest, manual_insert)
{
    this->init(false);
    EXPECT_EQ(0, this->get_active_nodes());

    this->index->set_node(1, std::vector<uint32_t>());
    EXPECT_EQ(1, this->get_active_nodes());
    this->index->set_node(2, std::vector<uint32_t>());
    EXPECT_EQ(2, this->get_active_nodes());

    HnswTestNode three{{1,2}};
    this->index->set_node(3, three);
    this->expect_level_0(1, {3});
    this->expect_level_0(2, {3});
    this->expect_level_0(3, {1,2});
    EXPECT_EQ(3, this->get_active_nodes());

    this->expect_entry_point(1, 0);

    HnswTestNode twolevels{{{1},std::vector<uint32_t>()}};
    this->index->set_node(4, twolevels);

    this->expect_entry_point(4, 1);
    this->expect_level_0(1, {3,4});
    EXPECT_EQ(4, this->get_active_nodes());

    HnswTestNode five{{{1,2}, {4}}};
    this->index->set_node(5, five);

    this->expect_levels(1, {{3,4,5}});
    this->expect_levels(2, {{3,5}});
    this->expect_levels(3, {{1,2}});
    this->expect_levels(4, {{1}, {5}});
    this->expect_levels(5, {{1,2}, {4}});
    EXPECT_EQ(5, this->get_active_nodes());
}

TYPED_TEST(HnswIndexTest, memory_is_reclaimed_when_doing_changes_to_graph)
{
    this->init(false);

    this->add_document(1);
    this->add_document(3);
    auto mem_1 = this->memory_usage();

    this->add_document(2);
    this->expect_level_0(1, {2,3});
    this->expect_level_0(2, {1,3});
    this->expect_level_0(3, {1,2});
    EXPECT_EQ(3, this->get_active_nodes());
    auto mem_2 = this->memory_usage();
    // We should use more memory with larger link arrays and extra document.
    EXPECT_GT((mem_2.usedBytes() - mem_2.deadBytes()), (mem_1.usedBytes() - mem_1.deadBytes()));
    EXPECT_EQ(0, mem_2.allocatedBytesOnHold());

    this->remove_document(2);
    EXPECT_EQ(2, this->get_active_nodes());
    size_t nodes_growth = 0;
    if constexpr (TestFixture::is_single) {
        this->expect_level_0(1, {3});
        this->expect_empty_level_0(2);
        this->expect_level_0(3, {1});
    } else {
        // managed nodeid mapping, docid 1 => 1, docid 3 => 2
        this->expect_level_0(1, {2});
        this->expect_empty_level_0(3);
        this->expect_level_0(2, {1});
        nodes_growth = sizeof(HnswNode); // Entry for nodeid 3 added when adding doc 2
    }
    auto mem_3 = this->memory_usage();
    // We end up in the same state as before document 2 was added and effectively use the same amount of memory.
    EXPECT_EQ((mem_1.usedBytes() - mem_1.deadBytes() + nodes_growth), (mem_3.usedBytes() - mem_3.deadBytes()));
    EXPECT_EQ(0, mem_3.allocatedBytesOnHold());
    EXPECT_EQ(2, this->get_active_nodes());
}

TYPED_TEST(HnswIndexTest, memory_is_put_on_hold_while_read_guard_is_held)
{
    this->init(true);

    this->add_document(1);
    this->add_document(3);
    {
        auto guard = this->take_read_guard();
        this->add_document(2);
        auto mem = this->memory_usage();
        // As read guard is held memory to reclaim is put on hold
        EXPECT_GT(mem.allocatedBytesOnHold(), 0);
    }
    this->commit();
    auto mem = this->memory_usage();
    EXPECT_EQ(0, mem.allocatedBytesOnHold());
}

TYPED_TEST(HnswIndexTest, shrink_called_simple)
{
    this->init(false);
    HnswTestNode empty{std::vector<uint32_t>()};
    this->index->set_node(1, empty);
    HnswTestNode nb1{std::vector<uint32_t>(1, 1)};
    this->index->set_node(2, nb1);
    this->index->set_node(3, nb1);
    this->index->set_node(4, nb1);
    this->index->set_node(5, nb1);
    this->expect_level_0(1, {2,3,4,5});
    this->index->set_node(6, nb1);
    this->expect_level_0(1, {2,3,4,5,6});
    this->expect_level_0(2, {1});
    this->expect_level_0(3, {1});
    this->expect_level_0(4, {1});
    this->expect_level_0(5, {1});
    this->expect_level_0(6, {1});
    this->index->set_node(7, nb1);
    this->expect_level_0(1, {2,3,4,6,7});
    this->expect_level_0(5, {});
    this->expect_level_0(6, {1});
    this->index->set_node(8, nb1);
    this->expect_level_0(1, {2,3,4,7,8});
    this->expect_level_0(6, {});
    this->index->set_node(9, nb1);
    this->expect_level_0(1, {2,3,4,7,8});
    this->expect_level_0(2, {1});
    this->expect_level_0(3, {1});
    this->expect_level_0(4, {1});
    this->expect_level_0(5, {});
    this->expect_level_0(6, {});
    this->expect_level_0(7, {1});
    this->expect_level_0(8, {1});
    this->expect_level_0(9, {});
    EXPECT_TRUE(this->index->check_link_symmetry());
}

TYPED_TEST(HnswIndexTest, shrink_called_heuristic)
{
    this->init(true);
    HnswTestNode empty{std::vector<uint32_t>()};
    this->index->set_node(1, empty);
    HnswTestNode nb1{std::vector<uint32_t>(1, 1)};
    this->index->set_node(2, nb1);
    this->index->set_node(3, nb1);
    this->index->set_node(4, nb1);
    this->index->set_node(5, nb1);
    this->expect_level_0(1, {2,3,4,5});
    this->index->set_node(6, nb1);
    this->expect_level_0(1, {2,3,4,5,6});
    this->expect_level_0(2, {1});
    this->expect_level_0(3, {1});
    this->expect_level_0(4, {1});
    this->expect_level_0(5, {1});
    this->expect_level_0(6, {1});
    this->index->set_node(7, nb1);
    this->expect_level_0(1, {2,3,4});
    this->expect_level_0(2, {1});
    this->expect_level_0(3, {1});
    this->expect_level_0(4, {1});
    this->expect_level_0(5, {});
    this->expect_level_0(6, {});
    this->expect_level_0(7, {});
    this->index->set_node(8, nb1);
    this->index->set_node(9, nb1);
    this->expect_level_0(1, {2,3,4,8,9});
    EXPECT_TRUE(this->index->check_link_symmetry());
}

namespace {

template <class ResultGraph, HnswIndexType type>
ResultGraph
make_graph_helper(HnswIndex<type>& index)
{
    using LevelArrayRef = typename HnswGraph<type>::LevelArrayRef;
    using LinkArrayRef = typename HnswGraph<type>::LinkArrayRef;
    auto& graph = index.get_graph();
    ResultGraph result(graph.size());
    assert(!graph.get_levels_ref(0).valid());
    for (uint32_t doc_id = 1; doc_id < graph.size(); ++doc_id) {
        auto& node = result[doc_id];
        auto levels_ref = graph.get_levels_ref(doc_id);
        if constexpr (std::is_same_v<std::remove_reference_t<decltype(node)>, uint32_t>) {
            node = levels_ref.ref();
        } else {
            LevelArrayRef level_array(graph.get_level_array(levels_ref));
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

template <HnswIndexType type>
LinkGraph
make_link_graph(HnswIndex<type>& index)
{
    return make_graph_helper<LinkGraph, type>(index);
}

using LinkArrayRefGraph = std::vector<std::vector<uint32_t>>;

template <HnswIndexType type>
LinkArrayRefGraph
make_link_array_refs(HnswIndex<type>& index)
{
    return make_graph_helper<LinkArrayRefGraph, type>(index);
}

using LevelArrayRefGraph = std::vector<uint32_t>;

template <HnswIndexType type>
LevelArrayRefGraph
make_level_array_refs(HnswIndex<type>& index)
{
    return make_graph_helper<LevelArrayRefGraph, type>(index);
}

}

TYPED_TEST(HnswIndexTest, hnsw_graph_is_compacted)
{
    this->init(true);
    this->get_vectors().clear();
    uint32_t doc_id = 1;
    for (uint32_t x = 0; x < 100; ++x) {
        for (uint32_t y = 0; y < 50; ++y) {
            this->get_vectors().set(doc_id, { float(x), float(y) });
            ++doc_id;
        }
    }
    uint32_t doc_id_end = doc_id;
    for (doc_id = 2; doc_id < doc_id_end; ++doc_id) {
        this->add_document(doc_id);
    }
    this->add_document(1);
    for (doc_id = 10; doc_id < doc_id_end; ++doc_id) {
        this->remove_document(doc_id);
    }
    auto mem_1 = this->commit_and_update_stat();
    auto link_graph_1 = make_link_graph(*this->index);
    auto link_array_refs_1 = make_link_array_refs(*this->index);
    auto level_array_refs_1 = make_level_array_refs(*this->index);
    // Normal compaction
    EXPECT_TRUE(this->index->consider_compact(CompactionStrategy()));
    auto mem_2 = this->commit_and_update_stat();
    EXPECT_LT(mem_2.usedBytes(), mem_1.usedBytes());
    for (uint32_t i = 0; i < 10; ++i) {
        mem_1 = mem_2;
        // Forced compaction to move things around
        CompactionSpec compaction_spec(true, false);
        CompactionStrategy compaction_strategy;
        auto& graph = this->index->get_graph();
        graph.links_store.set_compaction_spec(compaction_spec);
        graph.levels_store.set_compaction_spec(compaction_spec);
        this->index->compact_link_arrays(compaction_strategy);
        this->index->compact_level_arrays(compaction_strategy);
        this->commit();
        this->index->update_stat(compaction_strategy);
        mem_2 = this->commit_and_update_stat();
        if (mem_2.usedBytes() == mem_1.usedBytes()) {
            break;
        }
    }
    auto link_graph_2 = make_link_graph(*this->index);
    auto link_array_refs_2 = make_link_array_refs(*this->index);
    auto level_array_refs_2 = make_level_array_refs(*this->index);
    EXPECT_EQ(link_graph_1, link_graph_2);
    EXPECT_NE(link_array_refs_1, link_array_refs_2);
    EXPECT_NE(level_array_refs_1, level_array_refs_2);
    this->index->shrink_lid_space(10);
    auto mem_3 = this->commit_and_update_stat();
    if constexpr (std::is_same_v<typename TypeParam::IdMapping, HnswIdentityMapping>) {
        EXPECT_LT(mem_3.usedBytes(), mem_2.usedBytes());
    } else {
        EXPECT_EQ(mem_3.usedBytes(), mem_2.usedBytes());
    }
}

TYPED_TEST(HnswIndexTest, hnsw_graph_can_be_saved_and_loaded)
{
    this->init(false);
    this->make_savetest_index();
    this->check_savetest_index("before save");
    auto data = this->save_index();
    this->init(false);
    this->load_index(data);
    this->check_savetest_index("after load");
}

TYPED_TEST(HnswIndexTest, search_during_remove)
{
    this->init(false);
    this->make_savetest_index();
    this->writer_clears_tensor(4);
    this->expect_top_3_by_docid("{0, 0}", {0, 0}, {7});
}

using HnswMultiIndexTest = HnswIndexTest<HnswIndex<HnswIndexType::MULTI>>;

namespace {

class MyGlobalFilter : public GlobalFilter {
    std::shared_ptr<GlobalFilter> _filter;
    mutable uint32_t              _max_docid;
public:
    explicit MyGlobalFilter(std::shared_ptr<GlobalFilter> filter) noexcept
        : _filter(std::move(filter)),
          _max_docid(0)
    {
    }
    bool is_active() const override { return _filter->is_active(); }
    uint32_t size() const override { return _filter->size(); }
    uint32_t count() const override { return _filter->count(); }
    bool check(uint32_t docid) const override {
        _max_docid = std::max(_max_docid, docid);
        return _filter->check(docid);
    }
    uint32_t max_docid() const noexcept { return _max_docid; }
};

}

TEST_F(HnswMultiIndexTest, duplicate_docid_is_removed)
{
    this->init(false);
    this->vectors
        .set(1, {0, 0, 0, 2})
        .set(2, {1, 0})
        .set(3, {1, 2})
        .set(4, {2, 0, 2, 2});
    /*
     * Graph showing documents at column x row y, origo in lower left corner.
     *
     *   1 3 4
     *   . . .
     *   1 2 4
     */
    for (uint32_t docid = 1; docid < 5; ++docid) {
        this->add_document(docid);
    }
    this->expect_top_3_by_docid("{0, 0}", {0, 0}, {1, 2, 4});
    this->expect_top_3_by_docid("{0, 1}", {0, 1}, {1, 2, 3});
    this->expect_top_3_by_docid("{0, 2}", {0, 2}, {1, 3, 4});
    this->expect_top_3_by_docid("{1, 0}", {1, 0}, {1, 2, 4});
    this->expect_top_3_by_docid("{1, 2}", {1, 2}, {1, 3, 4});
    this->expect_top_3_by_docid("{2, 0}", {2, 0}, {1, 2, 4});
    this->expect_top_3_by_docid("{2, 1}", {2, 1}, {2, 3, 4});
    this->expect_top_3_by_docid("{2, 2}", {2, 2}, {1, 3, 4});
    this->reset_doom(-1s); // 1s beyond doom => no hits
    this->expect_top_3_by_docid("{2, 2}", {2, 2}, {});
    this->reset_doom();
    this->expect_top_3_by_docid("{2, 2}", {2, 2}, {1, 3, 4});
    auto filter = std::make_shared<MyGlobalFilter>(GlobalFilter::create({1, 2}, 3));
    global_filter = filter;
    this->expect_top_3_by_docid("{2,2}", {2, 2}, {1, 2});
    EXPECT_EQ(2, filter->max_docid());
}

TEST_F(HnswMultiIndexTest, docid_with_empty_tensor_can_be_removed)
{
    this->init(false);
    this->vectors.set(1, {});
    this->add_document(1);
    this->remove_document(1);
}

TEST_F(HnswMultiIndexTest, docid_with_empty_tensor_can_be_removed_after_restart)
{
    this->init(false);
    this->vectors.set(1, {});
    this->add_document(1);
    auto data = this->save_index();
    this->init(false);
    this->load_index(data);
    this->remove_document(1);
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
    for (unsigned int l : hist) {
        double expected = left * 0.75;
        EXPECT_TRUE(l < expected*1.01 + 100);
        EXPECT_TRUE(l > expected*0.99 - 100);
        left *= 0.25;
    }
    EXPECT_TRUE(hist.size() < 14);
}

template <typename IndexType>
class TwoPhaseTest : public HnswIndexTest<IndexType> {
public:
    TwoPhaseTest()
        : HnswIndexTest<IndexType>()
    {
        this->init(true);
        this->vectors.set(4, {1, 3}).set(5, {13, 3}).set(6, {7, 13})
               .set(1, {3, 7}).set(2, {7, 1}).set(3, {11, 7})
               .set(7, {6, 5}).set(8, {5, 5}).set(9, {6, 6});
    }
    ~TwoPhaseTest() override;
    using UP = std::unique_ptr<PrepareResult>;
    UP prepare_add(uint32_t docid, uint32_t max_level = 0) {
        this->level_generator->level = max_level;
        vespalib::GenerationHandler::Guard dummy;
        auto vectors_to_add = this->vectors.get_vectors(docid);
        return this->index->prepare_add_document(docid, vectors_to_add, dummy);
    }
    void complete_add(uint32_t docid, UP up) {
        this->index->complete_add_document(docid, std::move(up));
        this->commit();
    }

    uint32_t prepare_insert_during_remove_pass(bool heuristic_select_neighbors, uint32_t schedule_clear_tensor, const vespalib::string& label);
    void prepare_insert_during_remove(bool heuristic_select_neighbors);
};

template <typename IndexType>
TwoPhaseTest<IndexType>::~TwoPhaseTest() = default;

template <typename IndexType>
uint32_t
TwoPhaseTest<IndexType>::prepare_insert_during_remove_pass(bool heuristic_select_neighbors, uint32_t schedule_clear_tensor, const vespalib::string& label)
{
    SCOPED_TRACE(label);
    this->init(heuristic_select_neighbors);
    this->vectors.clear();
    this->vectors.set(4, {1, 3}).set(2, {7, 1}).set(7, {6, 5});
    this->make_savetest_index();
    auto old_get_vector_count = this->get_vector_count();
    this->set_schedule_clear_tensor(schedule_clear_tensor);
    this->clear_cleared_tensor_docid();
    auto prepared = this->prepare_add(2, 1);
    auto result = this->get_vector_count() - old_get_vector_count;
    auto cleared_tensor_docid = this->get_cleared_tensor_docid();
    if (cleared_tensor_docid != 0) {
        this->remove_document(cleared_tensor_docid);
    }
    this->complete_add(2, std::move(prepared));
    EXPECT_EQ(((cleared_tensor_docid == 0u) ? 3 : 2), this->get_active_nodes());
    return result;
}

template <typename IndexType>
void
TwoPhaseTest<IndexType>::prepare_insert_during_remove(bool heuristic_select_neighbors)
{
    auto get_vector_counts = prepare_insert_during_remove_pass(heuristic_select_neighbors, 0, "No clear tensor");
    for (uint32_t schedule_clear_tensor = 1; schedule_clear_tensor <= get_vector_counts; ++schedule_clear_tensor) {
        vespalib::asciistream os;
        os << "Writer thread cleared tensor for get_vector (" << schedule_clear_tensor << " of " << get_vector_counts << ")";
        prepare_insert_during_remove_pass(heuristic_select_neighbors, schedule_clear_tensor, os.str());
    }
}

TYPED_TEST_SUITE(TwoPhaseTest, HnswIndexTestTypes);

TYPED_TEST(TwoPhaseTest, two_phase_add)
{
    this->add_document(1);
    this->add_document(2);
    this->add_document(3);
    this->expect_entry_point(1, 0);
    this->add_document(4, 1);
    this->add_document(5, 1);
    this->add_document(6, 2);
    this->expect_entry_point(6, 2);

    this->expect_level_0(1, {2,4,6});
    this->expect_level_0(2, {1,3,4,5});
    this->expect_level_0(3, {2,5,6});

    this->expect_levels(4, {{1,2}, {5,6}});
    this->expect_levels(5, {{2,3}, {4,6}});
    this->expect_levels(6, {{1,3}, {4,5}, {}});
    EXPECT_EQ(6, this->get_active_nodes());

    auto up = this->prepare_add(7, 1);
    // simulate things happening while 7 is in progress:
    this->add_document(8); // added
    EXPECT_EQ(7, this->get_active_nodes());
    this->remove_document(1); // removed
    this->remove_document(5);
    EXPECT_EQ(5, this->get_active_nodes());
    this->vectors.set(5, {8, 14}); // updated and moved
    this->add_document(5, 2);
    this->add_document(9, 1); // added
    EXPECT_EQ(7, this->get_active_nodes());
    this->complete_add(7, std::move(up));
    EXPECT_EQ(8, this->get_active_nodes());

    auto& id_mapping = this->index->get_id_mapping();
    auto nodeids = id_mapping.get_ids(7);
    EXPECT_EQ(1, nodeids.size());
    // 1 filtered out because it was removed
    // 5 filtered out because it was updated
    this->expect_levels(nodeids[0], {{2}, {4}});
}

TYPED_TEST(TwoPhaseTest, prepare_insert_during_remove_simple_select_neighbors)
{
    this->prepare_insert_during_remove(false);
}

TYPED_TEST(TwoPhaseTest, prepare_insert_during_remove_heuristic_select_neighbors)
{
    this->prepare_insert_during_remove(true);
}

GTEST_MAIN_RUN_ALL_TESTS()
