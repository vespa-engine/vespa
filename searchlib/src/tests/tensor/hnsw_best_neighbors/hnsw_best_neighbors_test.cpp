// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/hnsw_multi_best_neighbors.h>
#include <vespa/searchlib/tensor/hnsw_single_best_neighbors.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <ostream>

using vespalib::datastore::EntryRef;
using search::tensor::HnswMultiBestNeighbors;
using search::tensor::HnswSingleBestNeighbors;
using search::tensor::NearestNeighborIndex;

using Neighbor = NearestNeighborIndex::Neighbor;

namespace search::tensor
{

std::ostream& operator<<(std::ostream& os, const Neighbor& neighbor) {
    os << "{ docid=" << neighbor.docid << ", distance=" << neighbor.distance << "}";
    return os;
}

}

struct DocidThenDistanceOrder
{
    bool operator()(const Neighbor& lhs, const Neighbor& rhs) const {
        if (lhs.docid != rhs.docid) {
            return lhs.docid < rhs.docid;
        }
        return lhs.distance < rhs.distance;
    }
};

template <typename BestNeighbors>
class HnswBestNeighborsTest : public testing::Test {
protected:
    BestNeighbors _neighbors;
    HnswBestNeighborsTest()
        : testing::Test(),
          _neighbors()
    {
        populate();
    }
    ~HnswBestNeighborsTest() override;

    void add(uint32_t nodeid, uint32_t docid, double distance) {
        _neighbors.emplace(nodeid, docid, EntryRef(), distance);
    }

    size_t size() const { return _neighbors.size(); }

    void assert_neighbors(std::vector<NearestNeighborIndex::Neighbor> exp, uint32_t k, double distance_limit) {
        auto neighbors_copy = _neighbors;
        auto act = neighbors_copy.get_neighbors(k, distance_limit);
        std::sort(act.begin(), act.end(), DocidThenDistanceOrder());
        EXPECT_EQ(exp, act);
    }

    void populate() {
        add(3, 3, 7.0);
        add(2, 2, 10.0);
        add(1, 1, 1.0);
        add(4, 2, 5.0);
    }
};

template <typename BestNeighbors>
HnswBestNeighborsTest<BestNeighbors>::~HnswBestNeighborsTest() = default;

using HnswBestNeighborsTypedTestTypes = testing::Types<HnswSingleBestNeighbors, HnswMultiBestNeighbors>;

TYPED_TEST_SUITE(HnswBestNeighborsTest, HnswBestNeighborsTypedTestTypes);

TYPED_TEST(HnswBestNeighborsTest, k_limit_is_enforced)
{
    this->assert_neighbors({}, 0, 40.0);
    this->assert_neighbors({{1, 1.0}}, 1, 40.0);
    this->assert_neighbors({{1, 1.0}, {2, 5.0}}, 2, 40.0);
    this->assert_neighbors({{1, 1.0}, {2, 5.0}, {3, 7.0}}, 3, 40.0);
}

TYPED_TEST(HnswBestNeighborsTest, distance_limit_is_enforced)
{
    this->assert_neighbors({}, 40, 0.5);
    this->assert_neighbors({{1, 1.0}}, 40, 1.0);
    this->assert_neighbors({{1, 1.0}, {2, 5.0}}, 40, 5.0);
    this->assert_neighbors({{1, 1.0}, {2, 5.0}, {3, 7.0}}, 40, 7.0);
}

using HnswSingleBestNeighborsTest = HnswBestNeighborsTest<HnswSingleBestNeighbors>;

TEST_F(HnswSingleBestNeighborsTest, duplicate_docids_are_not_elimiated)
{
    EXPECT_EQ(4, size());
    assert_neighbors({{1, 1.0}, {2, 5.0}, {2, 10.0}, {3, 7.0}}, 40, 40.0);
}

using HnswMultiBestNeighborsTest = HnswBestNeighborsTest<HnswMultiBestNeighbors>;

TEST_F(HnswMultiBestNeighborsTest, duplicate_docids_are_eliminated)
{
    EXPECT_EQ(3, size());
    assert_neighbors({{1, 1.0}, {2, 5.0}, {3, 7.0}}, 40, 40.0);
}

GTEST_MAIN_RUN_ALL_TESTS()
