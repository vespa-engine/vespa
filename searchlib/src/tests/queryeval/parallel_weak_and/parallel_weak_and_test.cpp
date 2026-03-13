// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/docid_with_weight_search_iterator.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/test/eagerchild.h>
#include <vespa/searchlib/queryeval/test/leafspec.h>
#include <vespa/searchlib/queryeval/test/wandspec.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_blueprint.h>
#include <vespa/searchlib/queryeval/wand/parallel_weak_and_search.h>
#include <vespa/searchlib/test/document_weight_attribute_helper.h>
#include <vespa/searchlib/test/weightedchildrenverifiers.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::query;
using namespace search::queryeval;
using namespace search::queryeval::test;

using feature_t = search::feature_t;
using score_t = wand::score_t;
using MatchParams = ParallelWeakAndSearch::MatchParams;
using RankParams = ParallelWeakAndSearch::RankParams;
using search::IDirectPostingStore;
using search::IDocidWithWeightPostingStore;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::test::DocumentWeightAttributeHelper;


struct Scores : public std::vector<score_t>
{
    Scores &add(score_t val) {
        push_back(val);
        return *this;
    }
};

struct ScoresHistory : public std::vector<Scores>
{
    ScoresHistory &add(const Scores &s) {
        push_back(s);
        return *this;
    }
};

std::ostream &operator << (std::ostream &out, const ScoresHistory &hist)
{
    out << "ScoresHistory:\n";
    for (size_t i = 0; i < hist.size(); ++i) {
        const Scores &scores = hist[i];
        out << "[" << i << "]: ";
        for (size_t j = 0; j < scores.size(); ++j) {
            if (j != 0) {
                out << ",";
            }
            out << scores[j];
        }
        out << std::endl;
    }
    return out;
}

struct TestHeap : public WeakAndHeap
{
    ScoresHistory history;

    explicit TestHeap(uint32_t scoresToTrack_) : WeakAndHeap(scoresToTrack_), history() {}
    void adjust(score_t *begin, score_t *end) override {
        Scores scores;
        for (score_t *itr = begin; itr != end; ++itr) {
            scores.add(*itr);
        }
        history.push_back(scores);
        setMinScore(1);
    }
    virtual size_t size() const { return history.size(); }
};

template <typename HeapType>
struct WandTestSpec : public WandSpec
{
    HeapType heap;
    TermFieldMatchData rootMatchData;
    MatchParams matchParams;
    MatchingPhase matching_phase;

    explicit WandTestSpec(uint32_t scoresToTrack, uint32_t scoresAdjustFrequency = 1,
                          score_t scoreThreshold = 0, double thresholdBoostFactor = 1);
    ~WandTestSpec();
    SearchIterator::UP create() {
        MatchData::UP childrenMatchData = createMatchData();
        MatchData *tmp = childrenMatchData.get();
        bool readonly_scores_heap = (matching_phase != MatchingPhase::FIRST_PHASE);
        return SearchIterator::UP(
                new TrackedSearch("PWAND", getHistory(),
                                  ParallelWeakAndSearch::create(
                                          getTerms(tmp),
                                          matchParams,
                                          RankParams(rootMatchData,
                                                     std::move(childrenMatchData)),
                                          true, readonly_scores_heap)));
    }
    void set_second_phase() { matching_phase = MatchingPhase::SECOND_PHASE; }
};

template <typename HeapType>
WandTestSpec<HeapType>::WandTestSpec(uint32_t scoresToTrack, uint32_t scoresAdjustFrequency,
                                     score_t scoreThreshold, double thresholdBoostFactor)
    : WandSpec(),
      heap(scoresToTrack),
      rootMatchData(),
      matchParams(heap, scoreThreshold, thresholdBoostFactor, scoresAdjustFrequency, 0),
      matching_phase(MatchingPhase::FIRST_PHASE)
{}

template <typename HeapType>
WandTestSpec<HeapType>::~WandTestSpec() = default;

using WandSpecWithTestHeap = WandTestSpec<TestHeap>;
using WandSpecWithRealHeap = WandTestSpec<SharedWeakAndPriorityQueue>;

FakeResult
doSearch(SearchIterator &sb, const TermFieldMatchData &tfmd)
{
    FakeResult retval;
    sb.initFullRange();
    for (sb.seek(1); ! sb.isAtEnd(); sb.seek(sb.getDocId() + 1)) {
        sb.unpack(sb.getDocId());
        retval.doc(sb.getDocId());
        feature_t score = tfmd.getRawScore();
        retval.score(score);
    }
    return retval;
}

SimpleResult
asSimpleResult(const FakeResult &result)
{
    SimpleResult retval;
    for (const auto & doc : result.inspect()) {
        retval.addHit(doc.docId);
    }
    return retval;
}

struct WandBlueprintSpec
{
    static const uint32_t fieldId = 0;
    static const TermFieldHandle handle = 0;
    std::vector<std::pair<std::string, int32_t> > tokens;
    uint32_t docIdLimit = 0;
    FakeRequestContext requestContext;

    WandBlueprintSpec &add(const std::string &token, int32_t weight) {
        tokens.emplace_back(token, weight);
        return *this;
    }

    Node::UP createNode(uint32_t scoresToTrack = 100,
                        score_t scoreThreshold = 0,
                        double thresholdBoostFactor = 1) const {
        auto node = std::make_unique<SimpleWandTerm>(tokens.size(), "view", 0, Weight(0),
                                                     scoresToTrack, scoreThreshold, thresholdBoostFactor);
        for (const auto & token : tokens) {
            node->addTerm(token.first, Weight(token.second));
        }
        return node;
    }

    Blueprint::UP blueprint(Searchable &searchable, const std::string &field, const search::query::Node &term) const {
        FieldSpecList fields;
        fields.add(FieldSpec(field, fieldId, handle));
        MatchDataLayout mdl;
        Blueprint::UP bp = searchable.createBlueprint(requestContext, fields, term, mdl);
        EXPECT_TRUE(mdl.empty());
        EXPECT_TRUE(dynamic_cast<ParallelWeakAndBlueprint*>(bp.get()) != nullptr);
        return bp;
    }

    SearchIterator::UP iterator(Searchable &searchable, const std::string &field) const {
        Node::UP term = createNode();
        Blueprint::UP bp = blueprint(searchable, field, *term);
        MatchData::UP md(MatchData::makeTestInstance(1, 1));
        bp->basic_plan(true, docIdLimit);
        bp->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP sb = bp->createSearch(*md);
        EXPECT_TRUE(dynamic_cast<ParallelWeakAndSearch*>(sb.get()) != nullptr);
        return sb;
    }

    FakeResult search(Searchable &searchable, const std::string &field) const {
        Node::UP term = createNode();
        return search(searchable, field, *term);
    }

    FakeResult search(Searchable &searchable, const std::string &field, const search::query::Node &term) const {
        Blueprint::UP bp = blueprint(searchable, field, term);
        MatchData::UP md(MatchData::makeTestInstance(1, 1));
        bp->basic_plan(true, docIdLimit);
        bp->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP sb = bp->createSearch(*md);
        EXPECT_TRUE(dynamic_cast<ParallelWeakAndSearch*>(sb.get()) != nullptr);
        return doSearch(*sb, *md->resolveTermField(handle));
    }
};

struct FixtureBase
{
    WandSpecWithRealHeap spec;
    FakeResult           result;
    FixtureBase(uint32_t scoresToTrack,
                uint32_t scoresAdjustFrequency,
                score_t scoreThreshold = 0,
                double boostFactor = 1.0)
        : spec(scoresToTrack, scoresAdjustFrequency, scoreThreshold, boostFactor),
          result() {}
    void prepare() {
        SearchIterator::UP si(spec.create());
        result = doSearch(*si, spec.rootMatchData);
    }
};

struct AlgoSimpleFixture : public FixtureBase
{
    AlgoSimpleFixture()
        : AlgoSimpleFixture(false)
    {
    }
    explicit AlgoSimpleFixture(bool second_phase)
        : FixtureBase(2, 1)
    {
        if (second_phase) {
            spec.set_second_phase();
        }
        spec.leaf(LeafSpec("A", 1).doc(1, 1).doc(2, 2).doc(3, 3).doc(4, 4).doc(5, 5).doc(6, 6));
        spec.leaf(LeafSpec("B", 4).doc(1, 1).doc(3, 3).doc(5, 5));
        prepare();
    }
};

struct AlgoAdvancedFixture : public FixtureBase
{
    AlgoAdvancedFixture() : FixtureBase(100, 1) {
        spec.leaf(LeafSpec("1").doc(1, 1).doc(11, 1).doc(111, 1));
        spec.leaf(LeafSpec("2").doc(2, 1).doc(12, 1).doc(112, 1));
        spec.leaf(LeafSpec("3").doc(3, 1).doc(13, 1).doc(113, 1));
        spec.leaf(LeafSpec("4").doc(4, 1).doc(14, 1).doc(114, 1));
        spec.leaf(LeafSpec("5").doc(5, 1).doc(15, 1).doc(115, 1));
        prepare();
    }
};

struct AlgoSubsearchFixture : public FixtureBase
{
    AlgoSubsearchFixture() : FixtureBase(2, 1) {
        spec.leaf(LeafSpec("A", 10).itr(new EagerChild(search::endDocId)));
        spec.leaf(LeafSpec("B", 20).itr(new EagerChild(10)));
        prepare();
    }
};

struct AlgoSameScoreFixture : public FixtureBase
{
    AlgoSameScoreFixture() : FixtureBase(1, 1) {
        spec.leaf(LeafSpec("A").doc(1, 1).doc(2, 1));
        prepare();
    }
};

struct AlgoScoreThresholdFixture : public FixtureBase
{
    explicit AlgoScoreThresholdFixture(score_t scoreThreshold) : FixtureBase(3, 1, scoreThreshold) {
        spec.leaf(LeafSpec("A", 1).doc(1, 10).doc(2, 30));
        spec.leaf(LeafSpec("B", 2).doc(1, 20).doc(3, 40));
        prepare();
    }
};

struct AlgoLargeScoresFixture : public FixtureBase
{
    explicit AlgoLargeScoresFixture(score_t scoreThreshold) : FixtureBase(3, 1, scoreThreshold) {
        spec.leaf(LeafSpec("A", 60000).doc(1, 60000).doc(2, 70000));
        spec.leaf(LeafSpec("B", 70000).doc(1, 80000).doc(3, 90000));
        prepare();
    }
};

struct AlgoExhaustPastFixture : public FixtureBase
{
    explicit AlgoExhaustPastFixture(score_t scoreThreshold) : FixtureBase(3, 1, scoreThreshold) {
        spec.leaf(LeafSpec("A", 1).doc(1, 20).doc(3, 40).doc(5, 10));
        spec.leaf(LeafSpec("B", 1).doc(5, 10));
        spec.leaf(LeafSpec("C", 1).doc(5, 10));
        prepare();
    }
};


TEST(ParallelWeakAndTest, require_that_algorithm_prunes_bad_hits_after_enough_good_ones_are_obtained)
{
    AlgoSimpleFixture f; // First phase
    FakeResult expect = FakeResult()
                        .doc(1).score(1 * 1 + 4 * 1)
                        .doc(2).score(1 * 2)
                        .doc(3).score(1 * 3 + 4 * 3)
                        .doc(5).score(1 * 5 + 4 * 5);
    EXPECT_EQ(expect, f.result);
}

TEST(ParallelWeakAndTest, require_that_algorithm_does_not_prune_hits_in_pater_matching_phases)
{
    AlgoSimpleFixture f(true); // Second phase
    FakeResult expect = FakeResult()
                        .doc(1).score(1 * 1 + 4 * 1)
                        .doc(2).score(1 * 2)
                        .doc(3).score(1 * 3 + 4 * 3)
                        .doc(4).score(1 * 4)
                        .doc(5).score(1 * 5 + 4 * 5)
                        .doc(6).score(1 * 6);
    EXPECT_EQ(expect, f.result);
}

TEST(ParallelWeakAndTest, require_that_algorithm_uses_subsearches_as_expected)
{
    AlgoSimpleFixture f;
    EXPECT_EQ(SearchHistory()
              .seek("PWAND", 1).seek("B", 1).step("B", 1).unpack("B", 1).step("PWAND", 1)
              .unpack("PWAND", 1).seek("A", 1).step("A", 1).unpack("A", 1)
              .seek("PWAND", 2).seek("B", 2).step("B", 3).seek("A", 2).step("A", 2).unpack("A", 2).step("PWAND", 2)
              .unpack("PWAND", 2)
              .seek("PWAND", 3).unpack("B", 3).step("PWAND", 3)
              .unpack("PWAND", 3).seek("A", 3).step("A", 3).unpack("A", 3)
              .seek("PWAND", 4).seek("B", 4).step("B", 5).seek("A", 4).step("A", 4).unpack("A", 4).unpack("B", 5).step("PWAND", 5)
              .unpack("PWAND", 5).seek("A", 5).step("A", 5).unpack("A", 5)
              .seek("PWAND", 6).seek("B", 6).step("B", search::endDocId).step("PWAND", search::endDocId),
              f.spec.getHistory());
}

TEST(ParallelWeakAndTest, require_that_algorithm_considers_documents_in_the_right_order)
{
    AlgoAdvancedFixture f;
    EXPECT_EQ(SimpleResult()
              .addHit(1).addHit(2).addHit(3).addHit(4).addHit(5)
              .addHit(11).addHit(12).addHit(13).addHit(14).addHit(15)
              .addHit(111).addHit(112).addHit(113).addHit(114).addHit(115), asSimpleResult(f.result));
}

TEST(ParallelWeakAndTest, require_that_algorithm_take_initial_docid_for_subsearches_into_account)
{
    AlgoSubsearchFixture f;
    EXPECT_EQ(FakeResult().doc(10).score(20), f.result);
    EXPECT_EQ(SearchHistory().seek("PWAND", 1).unpack("B", 10).step("PWAND", 10).unpack("PWAND", 10)
              .seek("PWAND", 11).seek("B", 11).step("B", search::endDocId).step("PWAND", search::endDocId),
              f.spec.getHistory());
}

TEST(ParallelWeakAndTest, require_that_algorithm_uses_first_match_when_two_matches_have_same_score)
{
    AlgoSameScoreFixture f;
    EXPECT_EQ(FakeResult().doc(1).score(100), f.result);
}

TEST(ParallelWeakAndTest, require_that_algorithm_uses_initial_score_threshold_case_all_hits_greater)
{
    AlgoScoreThresholdFixture f(29);
    EXPECT_EQ(FakeResult()
              .doc(1).score(1 * 10 + 2 * 20)
              .doc(2).score(1 * 30)
              .doc(3).score(2 * 40), f.result);
}

TEST(ParallelWeakAndTest, require_that_algorithm_uses_initial_score_threshold_case_2_hits_greater)
{
    AlgoScoreThresholdFixture f(30);
    EXPECT_EQ(FakeResult()
              .doc(1).score(1 * 10 + 2 * 20)
              .doc(3).score(2 * 40), f.result);
}

TEST(ParallelWeakAndTest, require_that_algorithm_uses_initial_score_threshold_case_1_hit_greater)
{
    AlgoScoreThresholdFixture f(50);
    EXPECT_EQ(FakeResult()
              .doc(3).score(2 * 40), f.result);
}

TEST(ParallelWeakAndTest, require_that_algorithm_uses_initial_score_threshold_case_0_hits_greater)
{
    AlgoScoreThresholdFixture f(80);
    EXPECT_EQ(FakeResult(), f.result);
}

TEST(ParallelWeakAndTest, require_that_algorithm_handles_large_scores)
{
    AlgoLargeScoresFixture f(60000L * 70000L);
    EXPECT_EQ(FakeResult()
              .doc(1).score(60000L * 60000L + 70000L * 80000L)
              .doc(3).score(70000L * 90000L), f.result);
}

TEST(ParallelWeakAndTest, require_that_algorithm_steps_all_present_terms_when_past_is_empty)
{
    AlgoExhaustPastFixture f(25);
    EXPECT_EQ(FakeResult()
              .doc(3).score(40)
              .doc(5).score(30), f.result);
}

struct HeapFixture
{
    WandSpecWithTestHeap spec;
    SimpleResult         result;
    HeapFixture() : spec(2, 2), result() {
        spec.leaf(LeafSpec("A", 1).doc(1, 1).doc(2, 2).doc(3, 3).doc(4, 4).doc(5, 5).doc(6, 6));
        SearchIterator::UP sb(spec.create());
        result.search(*sb, 10);
    }
    ~HeapFixture();
};

HeapFixture::~HeapFixture() = default;

TEST(ParallelWeakAndTest, require_that_scores_are_collected_in_batches_before_adjusting_heap)
{
    HeapFixture f;
    EXPECT_EQ(SimpleResult().addHit(1).addHit(2).addHit(3).addHit(4).addHit(5).addHit(6),
              f.result);
    EXPECT_EQ(ScoresHistory().add(Scores().add(1).add(2))
              .add(Scores().add(3).add(4))
              .add(Scores().add(5).add(6)),
              f.spec.heap.history);
}


struct SearchFixture : public FixtureBase
{
    SearchFixture() : FixtureBase(10, 1) {
        spec.leaf(LeafSpec("A", 1).doc(1, 10).doc(2, 30));
        spec.leaf(LeafSpec("B", 2).doc(1, 20).doc(3, 40));
        prepare();
    }
};

TEST(ParallelWeakAndTest, require_that_dot_product_score_is_calculated)
{
    SearchFixture f;
    FakeResult expect = FakeResult()
                        .doc(1).score(1 * 10 + 2 * 20)
                        .doc(2).score(1 * 30)
                        .doc(3).score(2 * 40);
    EXPECT_EQ(expect, f.result);
}


struct BlueprintFixtureBase
{
    WandBlueprintSpec spec;
    FakeSearchable    searchable;
    BlueprintFixtureBase();
    ~BlueprintFixtureBase();
    Blueprint::UP blueprint(const search::query::Node &term) {
        return spec.blueprint(searchable, "field", term);
    }
    SearchIterator::UP iterator() {
        return spec.iterator(searchable, "field");
    }
    FakeResult search(const search::query::Node &term) {
        return spec.search(searchable, "field", term);
    }
    FakeResult search() {
        return spec.search(searchable, "field");
    }
};

BlueprintFixtureBase::BlueprintFixtureBase() : spec(), searchable() {}
BlueprintFixtureBase::~BlueprintFixtureBase() = default;

struct BlueprintHitsFixture : public BlueprintFixtureBase
{
    static FakeResult createResult(size_t hits) {
        FakeResult result;
        for (size_t i = 0; i < hits; ++i) {
            result.doc(i + 1);
        }
        result.minMax(1, 10);
        return result;
    }
    BlueprintHitsFixture(size_t hits_a, size_t hits_b, size_t docs) : BlueprintFixtureBase() {
        spec.docIdLimit = docs + 1;
        spec.add("A", 20).add("B", 10);
        searchable.addResult("field", "A", createResult(hits_a));
        searchable.addResult("field", "B", createResult(hits_b));
    }
    bool maxScoreFirst() {
        SearchIterator::UP itr = iterator();
        const ParallelWeakAndSearch *wand = dynamic_cast<ParallelWeakAndSearch*>(itr.get());
        bool failed = false;
        EXPECT_EQ(2u, wand->get_num_terms()) << (failed = true, "");
        return failed ? false : (wand->get_term_weight(0) == 20);
    }
};

struct ThresholdBoostFixture : public FixtureBase
{
    FakeResult result;
    explicit ThresholdBoostFixture(double boost) : FixtureBase(1, 1, 800, boost) {
        spec.leaf(LeafSpec("A").doc(1, 10));
        spec.leaf(LeafSpec("B").doc(2, 20));
        spec.leaf(LeafSpec("C").doc(3, 30));
        spec.leaf(LeafSpec("D").doc(4, 42));
        SearchIterator::UP si(spec.create());
        result = doSearch(*si, spec.rootMatchData);
    }
    ~ThresholdBoostFixture();
};

ThresholdBoostFixture::~ThresholdBoostFixture() = default;

struct BlueprintFixture : public BlueprintFixtureBase
{
    BlueprintFixture() : BlueprintFixtureBase() {
        searchable.addResult("field", "A", FakeResult().doc(1).weight(10).pos(0).doc(2).weight(30).pos(0).minMax(0, 30));
        searchable.addResult("field", "B", FakeResult().doc(1).weight(20).pos(0).doc(3).weight(40).pos(0).minMax(0, 40));
        spec.add("A", 1).add("B", 2);
    }
};

struct BlueprintLargeScoresFixture : public BlueprintFixtureBase
{
    BlueprintLargeScoresFixture() : BlueprintFixtureBase() {
        searchable.addResult("field", "A", FakeResult().doc(1).weight(60000).pos(0).doc(2).weight(70000).pos(0).minMax(0, 70000));
        searchable.addResult("field", "B", FakeResult().doc(1).weight(80000).pos(0).doc(3).weight(90000).pos(0).minMax(0, 90000));
        spec.add("A", 60000).add("B", 70000);
    }
};

struct BlueprintAsStringFixture : public BlueprintFixtureBase
{
    BlueprintAsStringFixture() : BlueprintFixtureBase() {
        searchable.addResult("field", "A", FakeResult().doc(1).weight(10).pos(0).doc(2).weight(30).pos(0).minMax(0, 30));
        spec.add("A", 5);
    }
};


TEST(ParallelWeakAndTest, require_that_hit_estimate_is_calculated)
{
    BlueprintFixture f;
    Node::UP term = f.spec.createNode();
    Blueprint::UP bp = f.blueprint(*term);
    EXPECT_EQ(4u, bp->getState().estimate().estHits);
}

TEST(ParallelWeakAndTest, require_that_blueprint_picks_up_docid_limit)
{
    BlueprintFixture f;
    Node::UP term = f.spec.createNode(57, 67, 77.7);
    Blueprint::UP bp = f.blueprint(*term);
    const auto * pbp = dynamic_cast<const ParallelWeakAndBlueprint *>(bp.get());
    EXPECT_EQ(0u, pbp->get_docid_limit());
    bp->setDocIdLimit(1000);
    EXPECT_EQ(1000u, pbp->get_docid_limit());
}

TEST(ParallelWeakAndTest, require_that_scores_to_track_score_threshold_and_threshold_boost_factor_is_passed_down_from_query_node_to_blueprint)
{
    BlueprintFixture f;
    Node::UP term = f.spec.createNode(57, 67, 77.7);
    Blueprint::UP bp = f.blueprint(*term);
    const auto * pbp = dynamic_cast<const ParallelWeakAndBlueprint *>(bp.get());
    EXPECT_EQ(57u, pbp->getScores().getScoresToTrack());
    EXPECT_EQ(67u, pbp->getScoreThreshold());
    EXPECT_EQ(77.7, pbp->getThresholdBoostFactor());
}

TEST(ParallelWeakAndTest, require_that_search_iterator_is_correctly_setup_and_executed)
{
    BlueprintFixture f;
    FakeResult expect = FakeResult()
                        .doc(1).score(1 * 10 + 2 * 20)
                        .doc(2).score(1 * 30)
                        .doc(3).score(2 * 40);
    EXPECT_EQ(expect, f.search());
}

TEST(ParallelWeakAndTest, require_that_initial_score_threshold_can_be_specified_case_1_hit_greater)
{
    BlueprintFixture f;
    Node::UP term = f.spec.createNode(3, 50);
    EXPECT_EQ(FakeResult()
              .doc(3).score(2 * 40), f.search(*term));
}

TEST(ParallelWeakAndTest, require_that_large_scores_are_handled)
{
    BlueprintLargeScoresFixture f;
    Node::UP term = f.spec.createNode(3, 60000L * 70000L);
    EXPECT_EQ(FakeResult()
              .doc(1).score(60000L * 60000L + 70000L * 80000L)
              .doc(3).score(70000L * 90000L), f.search(*term));
}

TEST(ParallelWeakAndTest, require_that_docid_limit_is_propagated_to_search_iterator)
{
    BlueprintFixture f1;
    f1.spec.docIdLimit = 4050;
    SearchIterator::UP itr = f1.iterator();
    const ParallelWeakAndSearch *wand = dynamic_cast<ParallelWeakAndSearch*>(itr.get());
    EXPECT_EQ(4050u, wand->getMatchParams().docIdLimit);
}

TEST(ParallelWeakAndTest, require_that_terms_are_sorted_for_maximum_skipping)
{
    BlueprintHitsFixture f1(50, 50, 100);
    BlueprintHitsFixture f2(60, 50, 100);
    BlueprintHitsFixture f3(80, 50, 100);
    EXPECT_TRUE(f1.maxScoreFirst());
    EXPECT_TRUE(f2.maxScoreFirst());
    EXPECT_FALSE(f3.maxScoreFirst());
}

TEST(ParallelWeakAndTest, require_that_threshold_boosting_works_as_expected)
{
    ThresholdBoostFixture f1(1.0);
    ThresholdBoostFixture f2(2.0);
    EXPECT_EQ(FakeResult()
              .doc(1).score(1000)
              .doc(2).score(2000)
              .doc(3).score(3000)
              .doc(4).score(4200), f1.result);
    EXPECT_EQ(FakeResult()
              .doc(2).score(2000)
              .doc(4).score(4200), f2.result);
}

TEST(ParallelWeakAndTest, require_that_asString_on_blueprint_works)
{
    BlueprintAsStringFixture f;
    Node::UP term = f.spec.createNode(57, 67);
    Blueprint::UP bp = f.blueprint(*term);
    std::string expStr = "search::queryeval::ParallelWeakAndBlueprint {\n"
                              "    isTermLike: true\n"
                              "    fields: FieldList {\n"
                              "        [0]: Field {\n"
                              "            fieldId: 0\n"
                              "            handle: 0\n"
                              "            isFilter: false\n"
                              "        }\n"
                              "    }\n"
                              "    estimate: HitEstimate {\n"
                              "        empty: false\n"
                              "        estHits: 2\n"
                              "        cost_tier: 1\n"
                              "        tree_size: 2\n"
                              "        allow_termwise_eval: false\n"
                              "    }\n"
                              "    relative_estimate: 0\n"
                              "    cost: 0\n"
                              "    strict_cost: 0\n"
                              "    sourceId: 4294967295\n"
                              "    docid_limit: 0\n"
                              "    id: 0\n"
                              "    strict: false\n"
                              "    _weights: std::vector {\n"
                              "        [0]: 5\n"
                              "    }\n"
                              "    _terms: std::vector {\n"
                              "        [0]: search::queryeval::FakeBlueprint {\n"
                              "            isTermLike: true\n"
                              "            fields: FieldList {\n"
                              "                [0]: Field {\n"
                              "                    fieldId: 0\n"
                              "                    handle: 0\n"
                              "                    isFilter: false\n"
                              "                }\n"
                              "            }\n"
                              "            estimate: HitEstimate {\n"
                              "                empty: false\n"
                              "                estHits: 2\n"
                              "                cost_tier: 1\n"
                              "                tree_size: 1\n"
                              "                allow_termwise_eval: true\n"
                              "            }\n"
                              "            relative_estimate: 0\n"
                              "            cost: 0\n"
                              "            strict_cost: 0\n"
                              "            sourceId: 4294967295\n"
                              "            docid_limit: 0\n"
                              "            id: 0\n"
                              "            strict: false\n"
                              "        }\n"
                              "    }\n"
                              "}\n";
    EXPECT_EQ(expStr, bp->asString());
}

using MatchParams = ParallelWeakAndSearch::MatchParams;
using RankParams = ParallelWeakAndSearch::RankParams;

struct DummyHeap : public WeakAndHeap {
    DummyHeap() : WeakAndHeap(9001) {}
    void adjust(score_t *, score_t *) override {}
};

SearchIterator::UP create_wand(bool use_dww,
                               TermFieldMatchData &tfmd,
                               const MatchParams &matchParams,
                               const std::vector<int32_t> &weights,
                               const std::vector<IDirectPostingStore::LookupResult> &dict_entries,
                               const IDocidWithWeightPostingStore &attr,
                               bool strict)
{
    if (use_dww) {
        return ParallelWeakAndSearch::create(tfmd, matchParams, weights, dict_entries, attr, strict, false);
    }
    // use search iterators as children
    MatchDataLayout layout;
    std::vector<TermFieldHandle> handles;
    for (size_t i = 0; i < weights.size(); ++i) {
        handles.push_back(layout.allocTermField(tfmd.getFieldId()));
    }
    MatchData::UP childrenMatchData = layout.createMatchData();
    assert(childrenMatchData->getNumTermFields() == dict_entries.size());
    wand::Terms terms;
    for (size_t i = 0; i < dict_entries.size(); ++i) {
        terms.push_back(wand::Term(new DocidWithWeightSearchIterator(*(childrenMatchData->resolveTermField(handles[i])), attr, dict_entries[i]),
                                   weights[i],
                                   dict_entries[i].posting_size,
                                   childrenMatchData->resolveTermField(handles[i])));
    }
    assert(terms.size() == dict_entries.size());
    return SearchIterator::UP(ParallelWeakAndSearch::create(terms, matchParams, RankParams(tfmd, std::move(childrenMatchData)), strict, false));
}

class Verifier : public search::test::DwwIteratorChildrenVerifier {
public:
    explicit Verifier(bool use_dww) : _use_dww(use_dww) { }
private:
    SearchIterator::UP create(bool strict) const override {
        MatchParams match_params(_dummy_heap, _dummy_heap.getMinScore(), 1.0, 1, 0);
        std::vector<IDirectPostingStore::LookupResult> dict_entries;
        for (size_t i = 0; i < _num_children; ++i) {
            dict_entries.push_back(_helper.dww().lookup(vespalib::make_string("%zu", i).c_str(), _helper.dww().get_dictionary_snapshot()));
        }
        return create_wand(_use_dww, _tfmd, match_params, _weights, dict_entries, _helper.dww(), strict);
    }
    bool _use_dww;
    mutable DummyHeap _dummy_heap;
};

TEST(ParallelWeakAndTest, verify_search_iterator_conformance)
{
    for (bool use_dww: {false, true}) {
        Verifier verifier(use_dww);
        verifier.verify();
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
