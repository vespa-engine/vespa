// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/hnsw_graph.h>
#include <vespa/searchlib/tensor/hnsw_index_saver.h>
#include <vespa/searchlib/tensor/hnsw_index_loader.hpp>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("hnsw_save_load_test");

using namespace search::tensor;
using search::BufferWriter;
using search::fileutil::LoadedBuffer;

class VectorBufferWriter : public BufferWriter {
private:
    char tmp[1024];
public:
    std::vector<char> output;
    VectorBufferWriter() {
        setup(tmp, 1024);
    }
    ~VectorBufferWriter() {}
    void flush() override {
        for (size_t i = 0; i < usedLen(); ++i) {
            output.push_back(tmp[i]);
        }
        rewind();
    }
};

class VectorBufferReader {
private:
    const std::vector<char>& _data;
    size_t _pos;

public:
    VectorBufferReader(const std::vector<char>& data) : _data(data), _pos(0) {}
    uint32_t readHostOrder() {
        uint32_t result = 0;
        assert(_pos + sizeof(uint32_t) <= _data.size());
        std::memcpy(&result, _data.data() + _pos, sizeof(uint32_t));
        _pos += sizeof(uint32_t);
        return result;
    }
};

using V = std::vector<uint32_t>;

void populate(HnswGraph &graph) {
    // no 0
    graph.make_node(1, 1, 0, 1);
    auto er = graph.make_node(2, 2, 0, 2);
    // no 3
    graph.make_node(4, 4, 0, 2);
    graph.make_node(5, 5, 0, 0);
    graph.make_node(6, 6, 0, 1);

    graph.set_link_array(1, 0, V{2, 4, 6});
    graph.set_link_array(2, 0, V{1, 4, 6});
    graph.set_link_array(4, 0, V{1, 2, 6});
    graph.set_link_array(6, 0, V{1, 2, 4});
    graph.set_link_array(2, 1, V{4});
    graph.set_link_array(4, 1, V{2});
    graph.set_entry_node({2, er, 1});
}

void modify(HnswGraph &graph) {
    graph.remove_node(2);
    graph.remove_node(6);
    graph.make_node(7, 7, 0, 2);

    graph.set_link_array(1, 0, V{7, 4});
    graph.set_link_array(4, 0, V{7, 2});
    graph.set_link_array(7, 0, V{4, 2});
    graph.set_link_array(4, 1, V{7});
    graph.set_link_array(7, 1, V{4});

    graph.set_entry_node({4, graph.get_node_ref(4), 1});
}


class CopyGraphTest : public ::testing::Test {
public:
    HnswGraph original;
    HnswGraph copy;

    void expect_empty_d(uint32_t nodeid) const {
        EXPECT_FALSE(copy.acquire_node_ref(nodeid).valid());
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
        HnswIndexLoader<VectorBufferReader> loader(copy, std::make_unique<VectorBufferReader>(data));
        while (loader.load_next()) {}
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
    }
};

TEST_F(CopyGraphTest, reconstructs_graph)
{
    populate(original);
    auto data = save_original();
    load_copy(data);
    expect_copy_as_populated();
}

TEST_F(CopyGraphTest, later_changes_ignored)
{
    populate(original);
    HnswIndexSaver saver(original);
    modify(original);
    VectorBufferWriter vector_writer;
    saver.save(vector_writer);
    auto data = vector_writer.output;
    load_copy(data);
    expect_copy_as_populated();
}

GTEST_MAIN_RUN_ALL_TESTS()
