// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>
#include <vespa/searchlib/queryeval/wand/weak_and_heap.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/test/eagerchild.h>
#include <vespa/searchlib/queryeval/test/leafspec.h>
#include <vespa/searchlib/queryeval/test/wandspec.h>
#define ENABLE_GTEST_MIGRATION
#include <vespa/searchlib/test/weightedchildrenverifiers.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;
using namespace search::queryeval;
using namespace search::queryeval::test;

using History = SearchHistory;

namespace {

struct MyWandSpec : public WandSpec
{
    SharedWeakAndPriorityQueue scores;
    uint32_t n;

    explicit MyWandSpec(uint32_t n_in) : WandSpec(), scores(n_in), n(n_in) {}
    SearchIterator *create() {
        return new TrackedSearch("WAND", getHistory(),
                                 WeakAndSearch::create(getTerms(), wand::MatchParams(scores, 1, 1), n, true));
    }
};

struct SimpleWandFixture {
    MyWandSpec   spec;
    SimpleResult hits;
    SimpleWandFixture() : spec(2), hits() {
        spec.leaf(LeafSpec("foo").doc(1).doc(2).doc(3).doc(4).doc(5).doc(6));
        spec.leaf(LeafSpec("bar").doc(1).doc(3).doc(5));
        SearchIterator::UP search(spec.create());
        hits.search(*search);
    }
    ~SimpleWandFixture();
};

SimpleWandFixture::~SimpleWandFixture() = default;

struct AdvancedWandFixture {
    MyWandSpec   spec;
    SimpleResult hits;
    AdvancedWandFixture() : spec(100), hits() {
        spec.leaf(LeafSpec("1").doc(1).doc(11).doc(111));
        spec.leaf(LeafSpec("2").doc(2).doc(12).doc(112));
        spec.leaf(LeafSpec("3").doc(3).doc(13).doc(113));
        spec.leaf(LeafSpec("4").doc(4).doc(14).doc(114));
        spec.leaf(LeafSpec("5").doc(5).doc(15).doc(115));
        SearchIterator::UP search(spec.create());
        hits.search(*search);
    }
    ~AdvancedWandFixture();
};

AdvancedWandFixture::~AdvancedWandFixture() = default;

struct WeightOrder {
    bool operator()(const wand::Term &t1, const wand::Term &t2) const {
        return (t1.weight < t2.weight);
    }
};

} // namespace <unnamed>

TEST(WeakAndTest, require_that_wand_prunes_bad_hits_after_enough_good_ones_are_obtained)
{
    SimpleWandFixture f;
    EXPECT_EQ(SimpleResult().addHit(1).addHit(2).addHit(3).addHit(5), f.hits);
}

TEST(WeakAndTest, require_that_wand_uses_subsearches_as_expected)
{
    SimpleWandFixture f;
    EXPECT_EQ(History()
              .seek("WAND", 1).seek("bar", 1).step("bar", 1).step("WAND", 1)
              .unpack("WAND", 1).seek("foo", 1).step("foo", 1).unpack("bar", 1).unpack("foo", 1)
              .seek("WAND", 2).seek("bar", 2).step("bar", 3).seek("foo", 2).step("foo", 2).step("WAND", 2)
              .unpack("WAND", 2).unpack("foo", 2)
              .seek("WAND", 3).step("WAND", 3)
              .unpack("WAND", 3).seek("foo", 3).step("foo", 3).unpack("bar", 3).unpack("foo", 3)
              .seek("WAND", 4).seek("bar", 4).step("bar", 5).seek("foo", 5).step("foo", 5).step("WAND", 5)
              .unpack("WAND", 5).unpack("bar", 5).unpack("foo", 5)
              .seek("WAND", 6).seek("bar", 6).step("bar", search::endDocId).step("WAND", search::endDocId),
              f.spec.getHistory());
}

TEST(WeakAndTest, require_that_documents_are_considered_in_the_right_order)
{
    AdvancedWandFixture f;
    EXPECT_EQ(SimpleResult()
              .addHit(1).addHit(2).addHit(3).addHit(4).addHit(5)
              .addHit(11).addHit(12).addHit(13).addHit(14).addHit(15)
              .addHit(111).addHit(112).addHit(113).addHit(114).addHit(115), f.hits);
}

TEST(WeakAndTest, require_that_initial_docid_for_subsearches_are_taken_into_account)
{
    History history;
    wand::Terms terms;
    terms.push_back(wand::Term(new TrackedSearch("foo", history, new EagerChild(search::endDocId)), 100, 1));
    terms.push_back(wand::Term(new TrackedSearch("bar", history, new EagerChild(10)), 100, 2));
    SharedWeakAndPriorityQueue scores(2);
    auto search = std::make_unique<TrackedSearch>("WAND", history, WeakAndSearch::create(terms, wand::MatchParams(scores), 2, true));
    SimpleResult hits;
    hits.search(*search);
    EXPECT_EQ(SimpleResult().addHit(10), hits);
    EXPECT_EQ(History().seek("WAND", 1).step("WAND", 10).unpack("WAND", 10).unpack("bar", 10)
              .seek("WAND", 11).seek("bar", 11).step("bar", search::endDocId).step("WAND", search::endDocId),
              history);
}

class IteratorChildrenVerifier : public search::test::IteratorChildrenVerifier {
public:
    IteratorChildrenVerifier();
    ~IteratorChildrenVerifier() override;
private:
    mutable std::vector<std::unique_ptr<SharedWeakAndPriorityQueue>> _scores;
    SearchIterator::UP create(bool strict) const override {
        wand::Terms terms;
        for (size_t i = 0; i < _num_children; ++i) {
            terms.emplace_back(createIterator(_split_lists[i], strict).release(),
                               100, _split_lists[i].size());
        }
        static constexpr size_t LARGE_ENOUGH_HEAP_FOR_ALL = 10000;
        _scores.push_back(std::make_unique<SharedWeakAndPriorityQueue>(LARGE_ENOUGH_HEAP_FOR_ALL));
        return WeakAndSearch::create(terms, wand::MatchParams(*_scores.back(), 1, 1), -1, strict);
    }
};

IteratorChildrenVerifier::IteratorChildrenVerifier() : _scores() {}
IteratorChildrenVerifier::~IteratorChildrenVerifier() = default;

TEST(WeakAndTest, verify_search_iterator_conformance)
{
    IteratorChildrenVerifier verifier;
    verifier.verify();
}

GTEST_MAIN_RUN_ALL_TESTS()
