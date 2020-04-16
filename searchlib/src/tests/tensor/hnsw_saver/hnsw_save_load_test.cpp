// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/hnsw_graph.h>
#include <vespa/searchlib/tensor/hnsw_index_saver.h>
#include <vespa/searchlib/tensor/hnsw_index_loader.h>
#include <vespa/vespalib/util/bufferwriter.h>
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

using V = std::vector<uint32_t>;

void populate(HnswGraph &graph) {
    // no 0
    graph.make_node_for_document(1, 1);
    graph.make_node_for_document(2, 2);
    // no 3
    graph.make_node_for_document(4, 2);
    graph.make_node_for_document(5, 0);
    graph.make_node_for_document(6, 1);

    graph.set_link_array(1, 0, V{2, 4, 6});
    graph.set_link_array(2, 0, V{1, 4, 6});
    graph.set_link_array(4, 0, V{1, 2, 6});
    graph.set_link_array(6, 0, V{1, 2, 4});
    graph.set_link_array(2, 1, V{4});
    graph.set_link_array(4, 1, V{2});
    graph.set_entry_node(2, 1);
}

void modify(HnswGraph &graph) {
    graph.remove_node_for_document(2);
    graph.remove_node_for_document(6);
    graph.make_node_for_document(7, 2);

    graph.set_link_array(1, 0, V{7, 4});
    graph.set_link_array(4, 0, V{7, 2});
    graph.set_link_array(7, 0, V{4, 2});
    graph.set_link_array(4, 1, V{7});
    graph.set_link_array(7, 1, V{4});

    graph.set_entry_node(4, 1);
}


class CopyGraphTest : public ::testing::Test {
public:
    HnswGraph original;
    HnswGraph copy;

    void expect_empty_d(uint32_t docid) const {
        EXPECT_FALSE(copy.node_refs[docid].load_acquire().valid());
    }

    void expect_level_0(uint32_t docid, const V& exp_links) const {
        auto levels = copy.get_level_array(docid);
        EXPECT_GE(levels.size(), 1);
        auto links = copy.get_link_array(docid, 0);
        EXPECT_EQ(exp_links.size(), links.size());
        for (size_t i = 0; i < exp_links.size() && i < links.size(); ++i) {
            EXPECT_EQ(exp_links[i], links[i]);
        }
    }

    void expect_level_1(uint32_t docid, const V& exp_links) const {
        auto levels = copy.get_level_array(docid);
        EXPECT_EQ(2, levels.size());
        auto links = copy.get_link_array(docid, 1);
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
        HnswIndexLoader loader(copy);
        LoadedBuffer buffer(&data[0], data.size());
        loader.load(buffer);
    }

    void expect_copy_as_populated() const {
        EXPECT_EQ(copy.size(), 7);
        EXPECT_EQ(copy.entry_docid, 2);
        EXPECT_EQ(copy.entry_level, 1);

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
