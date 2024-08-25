// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/posting_list_merger.h>
#include <vespa/vespalib/util/size_literals.h>
#include <algorithm>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::btree::BTreeNoLeafData;
using search::attribute::PostingListMerger;

struct Posting {
    uint32_t lid;
    int32_t weight;
    Posting(uint32_t lid_, int32_t weight_) noexcept
        : lid(lid_),
          weight(weight_)
    {
    }

    bool operator==(const Posting &rhs) const noexcept {
        return ((lid == rhs.lid) && (weight == rhs.weight));
    }

    bool operator<(const Posting &rhs) const noexcept  { return lid < rhs.lid; }
};

std::ostream &operator<<(std::ostream &os, const Posting &posting)
{
    os << "{" << posting.lid << "," << posting.weight << "}";
    return os;
}


class WeightedPostingList
{
    std::vector<Posting> _entries;
public:
    WeightedPostingList(std::vector<Posting> entries) noexcept
        : _entries(std::move(entries))
    {
    }
    ~WeightedPostingList() = default;

    template <typename Func>
    void foreach(Func func) const {
        for (const auto &posting : _entries) {
            func(posting.lid, posting.weight);
        }
    }

    template <typename Func>
    void foreach_key(Func func) const {
        for (const auto &posting : _entries) {
            func(posting.lid);
        }
    }
};

constexpr uint32_t docIdLimit = 16_Ki;

class PostingListMergerTest : public ::testing::Test
{
protected:
    PostingListMerger<int32_t> _merger;

    PostingListMergerTest()
        : _merger(docIdLimit)
    {
    }

    ~PostingListMergerTest() override;

    void reserveArray(uint32_t postingsCount, size_t postingsSize) { _merger.reserveArray(postingsCount, postingsSize); }

    std::vector<Posting> asArray() {
        const auto &llArray = _merger.getArray();
        std::vector<Posting> result;
        result.reserve(llArray.size());
        for (auto &entry : llArray) {
            result.emplace_back(entry._key, entry.getData());
        }
        return result;
    }

    std::vector<uint32_t> bvAsArray() {
        const auto &bv = *_merger.getBitVector();
        std::vector<uint32_t> result;
        uint32_t lid = bv.getNextTrueBit(0);
        while (lid + 1 < docIdLimit) {
            result.emplace_back(lid);
            lid = bv.getNextTrueBit(lid + 1);
        }
        return result;
    }
};

PostingListMergerTest::~PostingListMergerTest() = default;

TEST_F(PostingListMergerTest, Single_weighted_array)
{
    _merger.reserveArray(1, 4);
    _merger.addToArray(WeightedPostingList({{ 2, 102}, {3, 103}, { 5, 105}, {9, 109}}));
    _merger.merge();
    EXPECT_EQ((std::vector<Posting>{{2, 102}, {3, 103}, {5, 105}, {9, 109}}), asArray());
}

TEST_F(PostingListMergerTest, Merge_array)
{
    _merger.reserveArray(2, 8);
    _merger.addToArray(WeightedPostingList({{ 2, 102}, {3, 103}, { 5, 105}, {9, 109}}));
    _merger.addToArray(WeightedPostingList({{ 6, 106}, {8, 108}, { 14, 114}, {17, 117}}));
    _merger.merge();
    EXPECT_EQ((std::vector<Posting>{{2, 102}, {3, 103}, {5, 105}, {6, 106}, {8, 108}, {9, 109}, {14, 114}, {17, 117}}), asArray());
}

TEST_F(PostingListMergerTest, Merge_many_arrays)
{
    std::vector<Posting> res;
    _merger.reserveArray(10, 100);
    for (uint32_t i = 0; i < 10; ++i) {
        std::vector<Posting> fragment;
        for (uint32_t j = 0; j < 10; ++j) {
            fragment.emplace_back(j * 100 + i, j * 200 + i);
        }
        _merger.addToArray(WeightedPostingList(fragment));
        res.insert(res.end(), fragment.begin(), fragment.end());
    }
    _merger.merge();
    std::sort(res.begin(), res.end());
    EXPECT_EQ(res, asArray());
}

TEST_F(PostingListMergerTest, Merge_single_posting_list_into_bitvector)
{
    _merger.allocBitVector();
    _merger.addToBitVector(WeightedPostingList({{ 2, 102}, {3, 103}, { 5, 105}, {9, 109}}));
    _merger.merge();
    EXPECT_EQ((std::vector<uint32_t>{2, 3, 5, 9}), bvAsArray());
}

TEST_F(PostingListMergerTest, Merge_multiple_posting_lists_into_bitvector)
{
    _merger.allocBitVector();
    _merger.addToBitVector(WeightedPostingList({{ 2, 102}, {3, 103}, { 5, 105}, {9, 109}}));
    _merger.addToBitVector(WeightedPostingList({{ 6, 106}, {8, 108}, { 14, 114}, {17, 117}}));
    _merger.merge();
    EXPECT_EQ((std::vector<uint32_t>{2, 3, 5, 6, 8, 9, 14, 17}), bvAsArray());
}

GTEST_MAIN_RUN_ALL_TESTS()
