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

using FloatVectors = MyDocVectorAccess<float>;
using FloatIndex = HnswIndex<float>;
using FloatIndexUP = std::unique_ptr<FloatIndex>;

class HnswIndexTest : public ::testing::Test {
public:
    FloatVectors vectors;
    FloatIndexUP index;

    HnswIndexTest()
        : vectors(),
          index()
    {
        vectors.set(1, {2, 2}).set(2, {3, 2}).set(3, {2, 3})
               .set(4, {1, 2}).set(5, {8, 3}).set(6, {7, 2})
               .set(7, {3, 5});
    }
    void init(bool heuristic_select_neighbors) {
        index = std::make_unique<FloatIndex>(vectors, HnswIndexBase::Config(2, 0, 10, heuristic_select_neighbors));
    }
    void expect_level_0(uint32_t docid, const HnswNode::LinkArray& exp_links) {
        auto node = index->get_node(docid);
        ASSERT_EQ(1, node.size());
        EXPECT_EQ(exp_links, node.level(0));
    }
};


TEST_F(HnswIndexTest, 2d_vectors_inserted_in_level_0_graph_with_simple_select_neighbors)
{
    init(false);

    index->add_document(1);
    expect_level_0(1, {});

    index->add_document(2);
    expect_level_0(1, {2});
    expect_level_0(2, {1});

    index->add_document(3);
    expect_level_0(1, {2, 3});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2});

    index->add_document(4);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2, 4});
    expect_level_0(4, {1, 3});

    index->add_document(5);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5});
    expect_level_0(3, {1, 2, 4, 5});
    expect_level_0(4, {1, 3});
    expect_level_0(5, {2, 3});

    index->add_document(6);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6});
    expect_level_0(3, {1, 2, 4, 5});
    expect_level_0(4, {1, 3});
    expect_level_0(5, {2, 3, 6});
    expect_level_0(6, {2, 5});

    index->add_document(7);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6, 7});
    expect_level_0(3, {1, 2, 4, 5, 7});
    expect_level_0(4, {1, 3});
    expect_level_0(5, {2, 3, 6});
    expect_level_0(6, {2, 5});
    expect_level_0(7, {2, 3});
}

TEST_F(HnswIndexTest, 2d_vectors_inserted_in_level_0_graph_with_heuristic_select_neighbors)
{
    init(true);

    index->add_document(1);
    expect_level_0(1, {});

    index->add_document(2);
    expect_level_0(1, {2});
    expect_level_0(2, {1});

    index->add_document(3);
    expect_level_0(1, {2, 3});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2});

    // Doc 4 is closest to 1 and they are linked.
    // Doc 4 is NOT linked to 3 as the distance between 4 and 3 is greater than the distance between 3 and 1.
    // Doc 3 is therefore reachable via 1. Same argument for why doc 4 is not linked to 2.
    index->add_document(4);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3});
    expect_level_0(3, {1, 2});
    expect_level_0(4, {1});

    // Doc 5 is closest to 2 and they are linked.
    // The other docs are reachable via 2, and no other links are created. Same argument as with doc 4 above.
    index->add_document(5);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5});
    expect_level_0(3, {1, 2});
    expect_level_0(4, {1});
    expect_level_0(5, {2});

    // Doc 6 is closest to 5 and they are linked.
    // Doc 6 is also linked to 2 as the distance between 6 and 2 is less than the distance between 2 and 5.
    index->add_document(6);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6});
    expect_level_0(3, {1, 2});
    expect_level_0(4, {1});
    expect_level_0(5, {2, 6});
    expect_level_0(6, {2, 5});

    // Doc 7 is closest to 3 and they are linked.
    // Doc 7 is also linked to 6 as the distance between 7 and 6 is less than the distance between 6 and 3.
    // Docs 1, 2, 4 are reachable via 3.
    index->add_document(7);
    expect_level_0(1, {2, 3, 4});
    expect_level_0(2, {1, 3, 5, 6});
    expect_level_0(3, {1, 2, 7});
    expect_level_0(4, {1});
    expect_level_0(5, {2, 6});
    expect_level_0(6, {2, 5, 7});
    expect_level_0(7, {3, 6});
}

GTEST_MAIN_RUN_ALL_TESTS()

