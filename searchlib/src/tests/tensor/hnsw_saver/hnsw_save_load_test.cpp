// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/hnsw_graph.h>
#include <vespa/searchlib/tensor/hnsw_identity_mapping.h>
#include <vespa/searchlib/tensor/hnsw_index_saver.h>
#include <vespa/searchlib/tensor/hnsw_index_loader.hpp>
#include <vespa/searchlib/tensor/hnsw_index_traits.h>
#include <vespa/searchlib/tensor/hnsw_nodeid_mapping.h>
#include <vespa/searchlib/test/vector_buffer_reader.h>
#include <vespa/searchlib/test/vector_buffer_writer.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("hnsw_save_load_test");

using namespace search::tensor;
using search::BufferWriter;
using search::fileutil::LoadedBuffer;
using search::test::VectorBufferReader;
using search::test::VectorBufferWriter;

using V = std::vector<uint32_t>;

template <HnswIndexType type>
uint32_t fake_docid(uint32_t nodeid);

template <>
uint32_t fake_docid<HnswIndexType::SINGLE>(uint32_t nodeid)
{
    return nodeid;
}

template <>
uint32_t fake_docid<HnswIndexType::MULTI>(uint32_t nodeid)
{
    switch (nodeid) {
    case 5:
        return 104;
    case 6:
        return 104;
    default:
        return nodeid + 100;
    }
}

template <HnswIndexType type>
uint32_t fake_subspace(uint32_t nodeid);

template <>
uint32_t fake_subspace<HnswIndexType::SINGLE>(uint32_t)
{
    return 0;
}

template <>
uint32_t fake_subspace<HnswIndexType::MULTI>(uint32_t nodeid)
{
    switch (nodeid) {
    case 5:
        return 2;
    case 6:
        return 1;
    default:
        return 0;
    }
}

template <typename NodeType>
uint32_t fake_get_docid(const NodeType& node, uint32_t nodeid);

template <>
uint32_t fake_get_docid<HnswSimpleNode>(const HnswSimpleNode &, uint32_t nodeid)
{
    return fake_docid<HnswIndexType::SINGLE>(nodeid);
}

template <>
uint32_t fake_get_docid<HnswNode>(const HnswNode& node, uint32_t)
{
    return node.acquire_docid();
}

template <HnswIndexType type>
void populate(HnswGraph<type> &graph) {
    // no 0
    graph.make_node(1, fake_docid<type>(1), fake_subspace<type>(1), 1);
    auto er = graph.make_node(2, fake_docid<type>(2), fake_subspace<type>(2), 2);
    // no 3
    graph.make_node(4, fake_docid<type>(4), fake_subspace<type>(4), 2);
    graph.make_node(5, fake_docid<type>(5), fake_subspace<type>(5), 0);
    graph.make_node(6, fake_docid<type>(6), fake_subspace<type>(6), 1);

    graph.set_link_array(1, 0, V{2, 4, 6});
    graph.set_link_array(2, 0, V{1, 4, 6});
    graph.set_link_array(4, 0, V{1, 2, 6});
    graph.set_link_array(6, 0, V{1, 2, 4});
    graph.set_link_array(2, 1, V{4});
    graph.set_link_array(4, 1, V{2});
    graph.set_entry_node({2, er, 1});
}

template <HnswIndexType type>
void modify(HnswGraph<type> &graph) {
    graph.remove_node(2);
    graph.remove_node(6);
    graph.make_node(7, fake_docid<type>(7), fake_subspace<type>(7), 2);

    graph.set_link_array(1, 0, V{7, 4});
    graph.set_link_array(4, 0, V{7, 2});
    graph.set_link_array(7, 0, V{4, 2});
    graph.set_link_array(4, 1, V{7});
    graph.set_link_array(7, 1, V{4});

    graph.set_entry_node({4, graph.get_levels_ref(4), 1});
}


template <typename GraphType>
class CopyGraphTest : public ::testing::Test {
public:
    GraphType original;
    GraphType copy;

    void expect_empty_d(uint32_t nodeid) const {
        EXPECT_FALSE(copy.acquire_levels_ref(nodeid).valid());
    }

    void expect_level_0(uint32_t nodeid, const V& exp_links) const {
        auto levels = copy.acquire_level_array(nodeid);
        EXPECT_GE(levels.size(), 1);
        auto links = copy.acquire_link_array(nodeid, 0);
        EXPECT_EQ(exp_links.size(), links.size());
        for (size_t i = 0; i < exp_links.size() && i < links.size(); ++i) {
            EXPECT_EQ(exp_links[i], links[i]);
        }
    }

    void expect_level_1(uint32_t nodeid, const V& exp_links) const {
        auto levels = copy.acquire_level_array(nodeid);
        EXPECT_EQ(2, levels.size());
        auto links = copy.acquire_link_array(nodeid, 1);
        EXPECT_EQ(exp_links.size(), links.size());
        for (size_t i = 0; i < exp_links.size() && i < links.size(); ++i) {
            EXPECT_EQ(exp_links[i], links[i]);
        }
    }

    std::vector<char> save_original() const {
        HnswIndexSaver saver(original);
        VectorBufferWriter vector_writer;
        saver.save(vector_writer);
        return vector_writer.output;
    }
    void load_copy(std::vector<char> data) {
        typename HnswIndexTraits<GraphType::index_type>::IdMapping id_mapping;
        HnswIndexLoader<VectorBufferReader, GraphType::index_type> loader(copy, id_mapping, std::make_unique<VectorBufferReader>(data));
        while (loader.load_next()) {}
    }

    void expect_docid_and_subspace(uint32_t nodeid) const {
        auto& node = copy.nodes.get_elem_ref(nodeid);
        EXPECT_EQ(fake_docid<GraphType::index_type>(nodeid), fake_get_docid(node, nodeid));
        EXPECT_EQ(fake_subspace<GraphType::index_type>(nodeid), node.acquire_subspace());
    }

    void expect_copy_as_populated() const {
        EXPECT_EQ(copy.size(), 7);
        auto entry = copy.get_entry_node();
        EXPECT_EQ(entry.nodeid, 2);
        EXPECT_EQ(entry.level, 1);

        expect_empty_d(0);
        expect_empty_d(3);
        expect_empty_d(5);

        expect_level_0(1, {2, 4, 6});
        expect_level_0(2, {1, 4, 6});
        expect_level_0(4, {1, 2, 6});
        expect_level_0(6, {1, 2, 4});
        
        expect_level_1(2, {4});
        expect_level_1(4, {2});
        expect_docid_and_subspace(1);
        expect_docid_and_subspace(2);
        expect_docid_and_subspace(4);
        expect_docid_and_subspace(6);
    }
};

using GraphTestTypes = ::testing::Types<HnswGraph<HnswIndexType::SINGLE>, HnswGraph<HnswIndexType::MULTI>>;

TYPED_TEST_SUITE(CopyGraphTest, GraphTestTypes);

TYPED_TEST(CopyGraphTest, reconstructs_graph)
{
    populate(this->original);
    auto data = this->save_original();
    this->load_copy(data);
    this->expect_copy_as_populated();
}

TYPED_TEST(CopyGraphTest, later_changes_ignored)
{
    populate(this->original);
    HnswIndexSaver saver(this->original);
    modify(this->original);
    VectorBufferWriter vector_writer;
    saver.save(vector_writer);
    auto data = vector_writer.output;
    this->load_copy(data);
    this->expect_copy_as_populated();
}

GTEST_MAIN_RUN_ALL_TESTS()
