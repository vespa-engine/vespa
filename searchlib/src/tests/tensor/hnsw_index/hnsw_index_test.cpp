// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/searchlib/tensor/doc_vector_access.h>
#include <vespa/searchlib/tensor/hnsw_index.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("hnsw_index_test");

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

class HnswIndexTest : public ::testing::Test {
public:
    MyDocVectorAccess<float> vectors;
    HnswIndex<float> index;

    HnswIndexTest()
        : vectors(),
          index(vectors, HnswIndexBase::Config(2, 0, 4))
    {
    }
    void expect_level_0(uint32_t docid, const HnswNode::LinkArray& exp_links) {
        auto node = index.get_node(docid);
        ASSERT_EQ(1, node.size());
        EXPECT_EQ(exp_links, node.level(0));
    }
};


TEST_F(HnswIndexTest, 2d_vectors_inserted_in_level_0_graph_with_simple_select_neighbors)
{
    vectors.set(1, {2, 2}).set(2, {3, 2}).set(3, {2, 3})
           .set(4, {1, 2}).set(5, {5, 3}).set(6, {6, 2});

    index.add_document(1);
    expect_level_0(1, {});

    index.add_document(2);
    expect_level_0(1, {2});
    expect_level_0(2, {1});

    index.add_document(3);
    expect_level_0(1, {2, 3});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2});

    index.add_document(4);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2, 4});
    expect_level_0(4, {1, 3});

    index.add_document(5);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5});
    expect_level_0(3, {1, 2, 4, 5});
    expect_level_0(4, {1, 3});
    expect_level_0(5, {2, 3});

    index.add_document(6);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6});
    expect_level_0(3, {1, 2, 4, 5});
    expect_level_0(4, {1, 3});
    expect_level_0(5, {2, 3, 6});
    expect_level_0(6, {2, 5});
}

GTEST_MAIN_RUN_ALL_TESTS()

