// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/i_direct_posting_store.h>
#include <vespa/searchlib/attribute/multi_term_or_filter_search.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>

using PostingList = search::attribute::PostingListTraits<int32_t>::PostingStoreBase;
using Iterator = search::attribute::PostingListTraits<int32_t>::const_iterator;
using KeyData = PostingList::KeyDataType;

using search::BitVector;
using search::attribute::MultiTermOrFilterSearch;
using search::fef::TermFieldMatchData;
using search::queryeval::SearchIterator;
using vespalib::datastore::EntryRef;

class MultiTermOrFilterSearchTest : public ::testing::Test {
    PostingList _postings;
    mutable TermFieldMatchData _tfmd;
    vespalib::GenerationHandler _gens;
    std::vector<EntryRef> _trees;
    uint32_t _range_start;
    uint32_t _range_end;
public:
    MultiTermOrFilterSearchTest();
    ~MultiTermOrFilterSearchTest() override;
    void inc_generation();
    size_t num_trees() const { return _trees.size(); }
    Iterator get_tree(size_t idx) const {
        if (idx < _trees.size()) {
            return _postings.beginFrozen(_trees[idx]);
        } else {
            return {};
        }
    }
    void ensure_tree(size_t idx) {
        if (idx <= _trees.size()) {
            _trees.resize(idx + 1);
        }
    }
    void add_tree(size_t idx, const std::vector<uint32_t>& keys) {
        ensure_tree(idx);
        std::vector<KeyData> adds;
        std::vector<uint32_t> removes;
        adds.reserve(keys.size());
        for (auto& key : keys) {
            adds.emplace_back(key, 1);
        }
        _postings.apply(_trees[idx], adds.data(), adds.data() + adds.size(), removes.data(), removes.data() + removes.size());
    }

    void clear_tree(size_t idx) {
        if (idx < _trees.size()) {
            _postings.clear(_trees[idx]);
            _trees[idx] = EntryRef();
        }
    }

    std::unique_ptr<SearchIterator> make_iterator() const {
        std::vector<Iterator> iterators;
        for (size_t i = 0; i < num_trees(); ++i) {
            iterators.emplace_back(get_tree(i));
        }
        auto result = MultiTermOrFilterSearch::create(std::move(iterators), _tfmd);
        result->initRange(_range_start, _range_end);
        return result;
    };

    std::vector<uint32_t> eval_daat(SearchIterator &iterator) const {
        std::vector<uint32_t> result;
        uint32_t doc_id = _range_start;
        while (doc_id < _range_end) {
            if (iterator.seek(doc_id)) {
                result.emplace_back(doc_id);
                iterator.unpack(doc_id);
                EXPECT_TRUE(_tfmd.has_ranking_data(doc_id));
                ++doc_id;
            } else {
                doc_id = std::max(doc_id + 1, iterator.getDocId());
            }
        }
        return result;
    }

    std::vector<uint32_t> frombv(const BitVector &bv) const {
        std::vector<uint32_t> result;
        uint32_t doc_id = _range_start;
        doc_id = bv.getNextTrueBit(doc_id);
        while (doc_id < _range_end) {
            result.emplace_back(doc_id);
            ++doc_id;
            doc_id = bv.getNextTrueBit(doc_id);
        }
        return result;
    }

    std::unique_ptr<BitVector> tobv(const std::vector<uint32_t> & values) const {
        auto bv = BitVector::create(_range_start, _range_end);
        for (auto value : values) {
            bv->setBit(value);
        }
        bv->invalidateCachedCount();
        return bv;
    }

    static void expect_result(const std::vector<uint32_t> & exp, const std::vector<uint32_t> & act)
    {
        EXPECT_EQ(exp, act);
    }

    void make_sample_data() {
        add_tree(0, { 10, 11 });
        add_tree(1, { 14, 17, 20 });
        add_tree(2, { 3 });
        add_tree(3, { 17 });
    }
    
    uint32_t get_range_start() const { return _range_start; }
    void set_range(uint32_t start, uint32_t end) {
        _range_start = start;
        _range_end = end;
    }
};

MultiTermOrFilterSearchTest::MultiTermOrFilterSearchTest()
    : _postings(true),
      _gens(),
      _range_start(1),
      _range_end(10000)
{
}

MultiTermOrFilterSearchTest::~MultiTermOrFilterSearchTest()
{
    for (auto& tree : _trees) {
        _postings.clear(tree);
    }
    _postings.clearBuilder();
    _postings.reclaim_all_memory();
    inc_generation();
}

void
MultiTermOrFilterSearchTest::inc_generation()
{
    _postings.freeze();
    _postings.assign_generation(_gens.getCurrentGeneration());
    _gens.incGeneration();
    _postings.reclaim_memory(_gens.get_oldest_used_generation());
}

TEST_F(MultiTermOrFilterSearchTest, daat_or)
{
    make_sample_data();
    expect_result(eval_daat(*make_iterator()), { 3, 10, 11, 14, 17, 20 });
}

TEST_F(MultiTermOrFilterSearchTest, taat_get_hits)
{
    make_sample_data();
    expect_result(frombv(*make_iterator()->get_hits(get_range_start())), { 3, 10, 11, 14, 17, 20 });
}

TEST_F(MultiTermOrFilterSearchTest, taat_or_hits_into)
{
    make_sample_data();
    auto bv = tobv({13, 14});
    make_iterator()->or_hits_into(*bv, get_range_start());
    expect_result(frombv(*bv), { 3, 10, 11, 13, 14, 17, 20 });
}

TEST_F(MultiTermOrFilterSearchTest, taat_and_hits_into)
{
    make_sample_data();
    auto bv = tobv({13, 14});
    make_iterator()->and_hits_into(*bv, get_range_start());
    expect_result(frombv(*bv), { 14 });
}

TEST_F(MultiTermOrFilterSearchTest, daat_or_ranged)
{
    make_sample_data();
    set_range(4, 15);
    expect_result(eval_daat(*make_iterator()), {10, 11, 14 });
}

TEST_F(MultiTermOrFilterSearchTest, taat_get_hits_ranged)
{
    make_sample_data();
    set_range(4, 15);
    expect_result(frombv(*make_iterator()->get_hits(get_range_start())), { 10, 11, 14 });
}

TEST_F(MultiTermOrFilterSearchTest, taat_or_hits_into_ranged)
{
    make_sample_data();
    set_range(4, 15);
    auto bv = tobv({13, 14});
    make_iterator()->or_hits_into(*bv, get_range_start());
    expect_result(frombv(*bv), { 10, 11, 13, 14 });
}

TEST_F(MultiTermOrFilterSearchTest, taat_and_hits_into_ranged)
{
    make_sample_data();
    set_range(4, 15);
    auto bv = tobv({13, 14});
    make_iterator()->and_hits_into(*bv, get_range_start());
    expect_result(frombv(*bv), { 14 });
}

namespace {

class Verifier : public search::test::SearchIteratorVerifier {
    MultiTermOrFilterSearchTest &_test;
public:
    Verifier(MultiTermOrFilterSearchTest &test, int num_trees)
        : _test(test)
    {
        std::vector<std::vector<uint32_t>> trees(num_trees);
        uint32_t tree_id = 0;
        for (const auto doc_id : getExpectedDocIds()) {
            trees[tree_id++ % trees.size()].emplace_back(doc_id);
        }
        tree_id = 0;
        for (const auto &tree : trees) {
            _test.add_tree(tree_id++, tree);
        }
        _test.inc_generation();
    }
    ~Verifier() override {
        for (uint32_t tree_id = 0; tree_id < _test.num_trees(); ++tree_id) {
            _test.clear_tree(tree_id);
        }
        _test.inc_generation();
    }
    std::unique_ptr<SearchIterator> create(bool) const override {
        return _test.make_iterator();
    }

};

TEST_F(MultiTermOrFilterSearchTest, iterator_conformance)
{
    {
        Verifier verifier(*this, 1);
        verifier.verify();
    }
    {
        Verifier verifier(*this, 2);
        verifier.verify();
    }
    {
        Verifier verifier(*this, 3);
        verifier.verify();
    }
}

}

GTEST_MAIN_RUN_ALL_TESTS()
