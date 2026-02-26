// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/queryeval/wand/weak_and_search.h>
#include <vespa/searchlib/queryeval/wand/weak_and_heap.h>
#include <vespa/searchlib/queryeval/matching_phase.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/test/eagerchild.h>
#include <vespa/searchlib/queryeval/test/leafspec.h>
#include <vespa/searchlib/queryeval/test/wandspec.h>
#include <vespa/searchlib/test/weightedchildrenverifiers.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;
using namespace search::queryeval;
using namespace search::queryeval::test;

using History = SearchHistory;

namespace {

constexpr uint32_t docid_limit = 116;

struct MyWandSpec : public WandSpec
{
    SharedWeakAndPriorityQueue scores;
    uint32_t n;
    MatchingPhase matching_phase;
    wand::MatchParams my_params;

    explicit MyWandSpec(uint32_t n_in)
        : WandSpec(),
          scores(n_in),
          n(n_in),
          matching_phase(MatchingPhase::FIRST_PHASE),
          my_params(scores, wand::StopWordStrategy::none(), 1, docid_limit)
    {}
    SearchIterator *create() {
        bool readonly_scores_heap = (matching_phase != MatchingPhase::FIRST_PHASE);
        return new TrackedSearch("WAND", getHistory(),
                                 WeakAndSearch::create(getTerms(), my_params, n, true, readonly_scores_heap));
    }
    void set_second_phase() { matching_phase = MatchingPhase::SECOND_PHASE; }
    void set_abs_stop_word_adjust_limit(double limit) {
        my_params.stop_words = wand::StopWordStrategy(-limit, 1.0, docid_limit, false);
    }
    SimpleResult search() {
        SearchIterator::UP search(create());
        SimpleResult hits;
        hits.search(*search, docid_limit);
        return hits;
    }
};

struct SimpleWandFixture {
    MyWandSpec   spec;
    SimpleResult hits;
    SimpleWandFixture()
        : SimpleWandFixture(false)
    {
    }
    explicit SimpleWandFixture(bool second_phase)
        : spec(2),
          hits()
    {
        if (second_phase) {
            spec.set_second_phase();
        }
        spec.leaf(LeafSpec("foo").doc(1).doc(2).doc(3).doc(4).doc(5).doc(6));
        spec.leaf(LeafSpec("bar").doc(1).doc(3).doc(5));
        SearchIterator::UP search(spec.create());
        hits.search(*search, docid_limit);
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
        hits.search(*search, docid_limit);
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
    SimpleWandFixture f; // First phase
    EXPECT_EQ(SimpleResult().addHit(1).addHit(2).addHit(3).addHit(5), f.hits);
}

TEST(WeakAndTest, require_that_wand_does_not_prune_hits_in_later_matching_phases)
{
    SimpleWandFixture f(true); // Second phase
    EXPECT_EQ(SimpleResult().addHit(1).addHit(2).addHit(3).addHit(4).addHit(5).addHit(6), f.hits);
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
    wand::MatchParams match_params(scores, wand::StopWordStrategy::none(), wand::DEFAULT_PARALLEL_WAND_SCORES_ADJUST_FREQUENCY, docid_limit);
    auto search = std::make_unique<TrackedSearch>("WAND", history, WeakAndSearch::create(terms, match_params, 2, true, false));
    SimpleResult hits;
    hits.search(*search, docid_limit);
    EXPECT_EQ(SimpleResult().addHit(10), hits);
    EXPECT_EQ(History().seek("WAND", 1).step("WAND", 10).unpack("WAND", 10).unpack("bar", 10)
              .seek("WAND", 11).seek("bar", 11).step("bar", search::endDocId).step("WAND", search::endDocId),
              history);
}

TEST(WeakAndTest, require_that_the_selected_adjust_word_must_match_when_using_stop_word_adjust_limit) {
    MyWandSpec spec(1000); // avoid limiting hits with heap
    spec.leaf(LeafSpec("1").doc(1).doc(2).doc(3).doc(4));
    spec.leaf(LeafSpec("2").doc(5).doc(6).doc(7));
    spec.leaf(LeafSpec("3").doc(8).doc(9));
    spec.set_abs_stop_word_adjust_limit(4);
    EXPECT_EQ(SimpleResult().addHit(1).addHit(2).addHit(3).addHit(4).addHit(5)
                      .addHit(6).addHit(7).addHit(8).addHit(9), spec.search());
    spec.set_abs_stop_word_adjust_limit(3);
    EXPECT_EQ(SimpleResult().addHit(5).addHit(6).addHit(7).addHit(8).addHit(9), spec.search());
    spec.set_abs_stop_word_adjust_limit(2);
    EXPECT_EQ(SimpleResult().addHit(8).addHit(9), spec.search());
    spec.set_abs_stop_word_adjust_limit(1);
    spec.getHistory()._entries.clear(); // only check history for last case
    EXPECT_EQ(SimpleResult().addHit(8).addHit(9), spec.search());
    EXPECT_EQ(History()
              .seek("WAND", 1).seek("3", 1).step("3", 8)
              .seek("2", 1).step("2", 5).seek("1", 5).step("1", search::endDocId) // 1+2 can compete with 3
              .step("WAND", 8)
              .unpack("WAND", 8).seek("2", 8).step("2", search::endDocId).unpack("3", 8)
              .seek("WAND", 9).seek("3", 9).step("3", 9).step("WAND", 9)
              .unpack("WAND", 9).unpack("3", 9)
              .seek("WAND", 10).seek("3", 10).step("3", search::endDocId).step("WAND", search::endDocId),
              spec.getHistory());
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
        return WeakAndSearch::create(terms, wand::MatchParams(*_scores.back(), wand::StopWordStrategy::none(), 1, docid_limit), -1, strict, false);
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
