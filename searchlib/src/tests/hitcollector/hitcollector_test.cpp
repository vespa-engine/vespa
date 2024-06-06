// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/queryeval/hitcollector.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("hitcollector_test");

using namespace search;
using namespace search::fef;
using namespace search::queryeval;

using ScoreMap = std::map<uint32_t, feature_t>;
using DocidVector = std::vector<uint32_t>;
using RankedHitVector = std::vector<RankedHit>;

using Ranges = std::pair<Scores, Scores>;

struct BasicScorer
{
    feature_t _scoreDelta;
    explicit BasicScorer(feature_t scoreDelta) : _scoreDelta(scoreDelta) {}
    feature_t score(uint32_t docid) const {
        return (docid + _scoreDelta);
    }
};

struct PredefinedScorer
{
    ScoreMap _scores;
    explicit PredefinedScorer(ScoreMap scores) : _scores(std::move(scores)) {}
    feature_t score(uint32_t docid) const {
        feature_t my_score = default_rank_value;
        auto itr = _scores.find(docid);
        if (itr != _scores.end()) {
            my_score = itr->second;
        }
        return my_score;
    }
};

std::vector<HitCollector::Hit> extract(SortedHitSequence seq) {
    std::vector<HitCollector::Hit> ret;
    while (seq.valid()) {
        ret.push_back(seq.get());
        seq.next();
    }
    return ret;
}

template <typename Scorer>
size_t do_reRank(const Scorer &scorer, HitCollector &hc, size_t count) {
    Ranges ranges;
    auto hits = extract(hc.getSortedHitSequence(count));
    for (auto &[docid, score]: hits) {
        ranges.first.update(score);
        score = scorer.score(docid);
        ranges.second.update(score);
    }
    hc.setRanges(ranges);
    hc.setReRankedHits(std::move(hits));
    return hc.getReRankedHits().size();
}

void checkResult(const ResultSet & rs, const std::vector<RankedHit> & exp)
{
    if ( ! exp.empty()) {
        const RankedHit * rh = rs.getArray();
        ASSERT_TRUE(rh != nullptr);
        ASSERT_EQ(rs.getArrayUsed(), exp.size());

        for (uint32_t i = 0; i < exp.size(); ++i) {
            EXPECT_EQ(rh[i].getDocId(), exp[i].getDocId());
            EXPECT_DOUBLE_EQ(rh[i].getRank() + 64.0, exp[i].getRank() + 64.0);
        }
    } else {
        ASSERT_TRUE(rs.getArray() == nullptr);
    }
}

void checkResult(ResultSet & rs, BitVector * exp)
{
    if (exp != nullptr) {
        BitVector * bv = rs.getBitOverflow();
        ASSERT_TRUE(bv != nullptr);
        bv->invalidateCachedCount();
        exp->invalidateCachedCount();
        LOG(info, "bv.hits: %u, exp.hits: %u", bv->countTrueBits(), exp->countTrueBits());
        ASSERT_TRUE(bv->countTrueBits() == exp->countTrueBits());
        EXPECT_TRUE(*bv == *exp);
    } else {
        ASSERT_TRUE(rs.getBitOverflow() == nullptr);
    }
}

void testAddHit(uint32_t numDocs, uint32_t maxHitsSize, const vespalib::string& label)
{

    SCOPED_TRACE(label);
    LOG(info, "testAddHit: no hits");
    {
        SCOPED_TRACE("no hits");
        HitCollector hc(numDocs, maxHitsSize);
        std::vector<RankedHit> expRh;

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        checkResult(*rs, expRh);
        checkResult(*rs, nullptr);
    }

    LOG(info, "testAddHit: only ranked hits");
    {
        SCOPED_TRACE("only ranked hits");
        HitCollector hc(numDocs, maxHitsSize);
        std::vector<RankedHit> expRh;

        for (uint32_t i = 0; i < maxHitsSize; ++i) {
            hc.addHit(i, i + 100);

            // build expected result set as we go along
            expRh.emplace_back();
            expRh.back()._docId = i;
            expRh.back()._rankValue = i + 100;
        }

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        checkResult(*rs, expRh);
        checkResult(*rs, nullptr);
    }

    LOG(info, "testAddHit: both ranked hits and bit vector hits");
    {
        SCOPED_TRACE("both ranked hits and bitvector hits");
        HitCollector hc(numDocs, maxHitsSize);
        std::vector<RankedHit> expRh;
        BitVector::UP expBv(BitVector::create(numDocs));

        for (uint32_t i = 0; i < numDocs; ++i) {
            hc.addHit(i, i + 100);

            // build expected result set as we go along
            expBv->setBit(i);
            if (i >= (numDocs - maxHitsSize)) {
                expRh.emplace_back();
                expRh.back()._docId = i;
                expRh.back()._rankValue = i + 100;
            }
        }

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        checkResult(*rs, expRh);
        checkResult(*rs, expBv.get());
    }
}

TEST(HitCollectorTest, testAddHit)
{
    testAddHit(30, 10, "numDocs==30");
    testAddHit(400, 10, "numDocs==400"); // 400/32 = 12 which is bigger than 10.
}

struct Fixture {
    HitCollector hc;
    BitVector::UP expBv;
    BasicScorer scorer;

    Fixture()
        : hc(20, 10), expBv(BitVector::create(20)), scorer(200)
    {
    }
    virtual ~Fixture() {}
    virtual HitRank calculateScore(uint32_t) { return 0; }
    void addHits() {
        for (uint32_t i = 0; i < 20; ++i) {
            hc.addHit(i, calculateScore(i));
            expBv->setBit(i);
        }
    }
    size_t reRank(size_t count) {
        return do_reRank(scorer, hc, count);
    }
    size_t reRank() { return reRank(5); }
};

struct AscendingScoreFixture : Fixture {
    AscendingScoreFixture() : Fixture() {}
    ~AscendingScoreFixture() override;
    HitRank calculateScore(uint32_t i) override {
        return i + 100;
    }
};

AscendingScoreFixture::~AscendingScoreFixture() = default;

struct DescendingScoreFixture : Fixture {
    DescendingScoreFixture() : Fixture() {}
    ~DescendingScoreFixture() override;
    HitRank calculateScore(uint32_t i) override {
        return 100 - i;
    }
};

DescendingScoreFixture::~DescendingScoreFixture() = default;

TEST(HitCollectorTest, rerank_empty)
{
    Fixture f;
    EXPECT_EQ(0u, f.reRank());
}

TEST(HitCollectorTest, rerank_ascending)
{
    AscendingScoreFixture f;
    f.addHits();
    EXPECT_EQ(5u, f.reRank());

    std::vector<RankedHit> expRh;
    for (uint32_t i = 10; i < 20; ++i) {  // 10 last are the best
        expRh.push_back(RankedHit(i, f.calculateScore(i)));
        if (i >= 15) { // hits from heap (5 last)
            expRh.back()._rankValue = i + 200; // after reranking
        }
    }
    EXPECT_EQ(expRh.size(), 10u);

    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    checkResult(*rs, expRh);
    checkResult(*rs, f.expBv.get());
}

TEST(HitCollectorTest, rerank_descending)
{
    DescendingScoreFixture f;
    f.addHits();
    EXPECT_EQ(5u, f.reRank());

    std::vector<RankedHit> expRh;
    for (uint32_t i = 0; i < 10; ++i) {  // 10 first are the best
        expRh.push_back(RankedHit(i, f.calculateScore(i)));
        if (i < 5) { // hits from heap (5 first)
            expRh.back()._rankValue = i + 200; // after reranking
        }
    }
    EXPECT_EQ(expRh.size(), 10u);

    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    checkResult(*rs, expRh);
    checkResult(*rs, f.expBv.get());
}

TEST(HitCollectorTest, rerank_partial)
{
    AscendingScoreFixture f;
    f.addHits();
    EXPECT_EQ(3u, f.reRank(3));

    std::vector<RankedHit> expRh;
    for (uint32_t i = 10; i < 20; ++i) {  // 10 last are the best
        expRh.push_back(RankedHit(i, f.calculateScore(i)));
        if (i >= 17) { // hits from heap (3 last)
            expRh.back()._rankValue = i + 200; // after reranking
        }
    }
    EXPECT_EQ(expRh.size(), 10u);

    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    checkResult(*rs, expRh);
    checkResult(*rs, f.expBv.get());
}

TEST(HitCollectorTest, require_that_hits_for_2nd_phase_candidates_can_be_retrieved)
{
    DescendingScoreFixture f;
    f.addHits();
    std::vector<HitCollector::Hit> scores = extract(f.hc.getSortedHitSequence(5));
    ASSERT_EQ(5u, scores.size());
    EXPECT_EQ(100, scores[0].second);
    EXPECT_EQ(99, scores[1].second);
    EXPECT_EQ(98, scores[2].second);
    EXPECT_EQ(97, scores[3].second);
    EXPECT_EQ(96, scores[4].second);
}

TEST(HitCollectorTest, require_that_score_ranges_can_be_read_and_set)
{
    std::pair<Scores, Scores> ranges = std::make_pair(Scores(1.0, 2.0), Scores(3.0, 4.0));
    HitCollector hc(20, 10);
    hc.setRanges(ranges);
    EXPECT_EQ(ranges.first.low, hc.getRanges().first.low);
    EXPECT_EQ(ranges.first.high, hc.getRanges().first.high);
    EXPECT_EQ(ranges.second.low, hc.getRanges().second.low);
    EXPECT_EQ(ranges.second.high, hc.getRanges().second.high);
}

TEST(HitCollectorTest, no_hits_to_rerank)
{
    uint32_t numDocs = 20;
    uint32_t maxHitsSize = 10;

    LOG(info, "testNoMDHeap: test it");
    {
        HitCollector hc(numDocs, maxHitsSize);
        std::vector<RankedHit> expRh;

        for (uint32_t i = 0; i < maxHitsSize; ++i) {
            hc.addHit(i, i + 100);

            // build expected result set as we go along
            expRh.emplace_back();
            expRh.back()._docId = i;
            expRh.back()._rankValue = i + 100;
        }

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        checkResult(*rs, expRh);
        checkResult(*rs, nullptr);
    }
}

void testScaling(const std::vector<feature_t> &initScores,
                 ScoreMap finalScores,
                 const std::vector<RankedHit> &expected)
{
    HitCollector hc(5, 5);

    // first phase ranking
    for (uint32_t i = 0; i < 5; ++i) {
        hc.addHit(i, initScores[i]);
    }

    PredefinedScorer scorer(std::move(finalScores));
    // perform second phase ranking
    EXPECT_EQ(2u, do_reRank(scorer, hc, 2));

    // check results
    std::unique_ptr<ResultSet> rs = hc.getResultSet();
    checkResult(*rs, expected);
}

TEST(HitCollectorTest, scaling)
{
    std::vector<feature_t> initScores(5);
    initScores[0] = 1000;
    initScores[1] = 2000;
    initScores[2] = 3000;
    initScores[3] = 4000;
    initScores[4] = 5000;

    // expected final rank scores
    std::vector<RankedHit> exp(5);
    for (uint32_t i = 0; i < 5; ++i) {
        exp[i]._docId = i;
    }

    {
        SCOPED_TRACE("scale down and adjust down");
        exp[0]._rankValue = 0;   // scaled
        exp[1]._rankValue = 100; // scaled
        exp[2]._rankValue = 200; // scaled
        exp[3]._rankValue = 300; // from heap
        exp[4]._rankValue = 400; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 300;
        finalScores[4] = 400;

        testScaling(initScores, std::move(finalScores), exp);
    }
    {
        SCOPED_TRACE("scale down and adjust up");
        exp[0]._rankValue = 200; // scaled
        exp[1]._rankValue = 300; // scaled
        exp[2]._rankValue = 400; // scaled
        exp[3]._rankValue = 500; // from heap
        exp[4]._rankValue = 600; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 500;
        finalScores[4] = 600;

        testScaling(initScores, std::move(finalScores), exp);
    }
    {
        SCOPED_TRACE("scale up and adjust down");
        exp[0]._rankValue = -500; // scaled (-500)
        exp[1]._rankValue = 750;  // scaled
        exp[2]._rankValue = 2000; // scaled
        exp[3]._rankValue = 3250; // from heap
        exp[4]._rankValue = 4500; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 3250;
        finalScores[4] = 4500;

        testScaling(initScores, std::move(finalScores), exp);
    }
    {
        SCOPED_TRACE("minimal scale (second phase range = 0 (4 - 4) -> 1)");
        exp[0]._rankValue = 1; // scaled
        exp[1]._rankValue = 2; // scaled
        exp[2]._rankValue = 3; // scaled
        exp[3]._rankValue = 4; // from heap
        exp[4]._rankValue = 4; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 4;
        finalScores[4] = 4;

        testScaling(initScores, std::move(finalScores), exp);
    }
    {
        SCOPED_TRACE("minimal scale (first phase range = 0 (4000 - 4000) -> 1)");
        std::vector<feature_t> is(initScores);
        is[4] = 4000;
        exp[0]._rankValue = -299600; // scaled
        exp[1]._rankValue = -199600; // scaled
        exp[2]._rankValue =  -99600; // scaled
        exp[3]._rankValue = 400; // from heap
        exp[4]._rankValue = 500; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 400;
        finalScores[4] = 500;

        testScaling(is, std::move(finalScores), exp);
    }
}

TEST(HitCollectorTest, only_bitvector)
{
    uint32_t numDocs = 20;
    LOG(info, "testOnlyBitVector: test it");
    {
        HitCollector hc(numDocs, 0);
        BitVector::UP expBv(BitVector::create(numDocs));

        for (uint32_t i = 0; i < numDocs; i += 2) {
            hc.addHit(i, i + 100);
            // build expected result set as we go along
            expBv->setBit(i);
        }

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        std::vector<RankedHit> expRh;
        checkResult(*rs, expRh);  // no ranked hits
        checkResult(*rs, expBv.get()); // only bit vector
    }
}

struct MergeResultSetFixture {
    const uint32_t numDocs;
    const uint32_t maxHitsSize;
    const uint32_t maxHeapSize;
    HitCollector hc;
    MergeResultSetFixture()
        : numDocs(100), maxHitsSize(80), maxHeapSize(30), hc(numDocs * 32, maxHitsSize)
    {}
};

TEST(HitCollectorTest, require_that_result_set_is_merged_correctly_with_first_phase_ranking)
{
    MergeResultSetFixture f;
    std::vector<RankedHit> expRh;
    for (uint32_t i = 0; i < f.numDocs; ++i) {
        f.hc.addHit(i, i + 1000);

        // build expected result set
        expRh.emplace_back();
        expRh.back()._docId = i;
        // only the maxHitsSize best hits gets a score
        expRh.back()._rankValue = (i < f.numDocs - f.maxHitsSize) ? default_rank_value : i + 1000;
    }
    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    checkResult(*rs, expRh);
}

void
addExpectedHitForMergeTest(const MergeResultSetFixture &f, std::vector<RankedHit> &expRh, uint32_t docId)
{
    expRh.emplace_back();
    expRh.back()._docId = docId;
    if (docId < f.numDocs - f.maxHitsSize) { // only the maxHitsSize best hits gets a score
        expRh.back()._rankValue = default_rank_value;
    } else if (docId < f.numDocs - f.maxHeapSize) { // only first phase ranking
        expRh.back()._rankValue = docId + 500; // adjusted with - 500
    } else { // second phase ranking on the maxHeapSize best hits
        expRh.back()._rankValue = docId + 500;
    }
}

TEST(HitCollectorTest, require_that_result_set_is_merged_correctly_with_second_phase_ranking_using_document_scorer)
{
    MergeResultSetFixture f;
    // with second phase ranking that triggers rescoring / scaling
    BasicScorer scorer(500); // second phase ranking setting score to docId + 500
    std::vector<RankedHit> expRh;
    for (uint32_t i = 0; i < f.numDocs; ++i) {
        f.hc.addHit(i, i + 1000);
        addExpectedHitForMergeTest(f, expRh, i);
    }
    EXPECT_EQ(f.maxHeapSize, do_reRank(scorer, f.hc, f.maxHeapSize));
    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    checkResult(*rs, expRh);
}

TEST(HitCollectorTest, require_that_hits_can_be_added_out_of_order)
{
    HitCollector hc(1000, 100);
    std::vector<RankedHit> expRh;
    // produce expected result in normal order
    for (uint32_t i = 0; i < 5; ++i) {
        expRh.emplace_back();
        expRh.back()._docId = i;
        expRh.back()._rankValue = i + 100;
    }
    // add results in reverse order
    for (uint32_t i = 5; i-- > 0; ) {
        hc.addHit(i, i + 100);
    }
    std::unique_ptr<ResultSet> rs = hc.getResultSet();
    checkResult(*rs, expRh);
    checkResult(*rs, nullptr);
}

TEST(HitCollectorTest, require_that_hits_can_be_added_out_of_order_when_passing_array_limit)
{
    HitCollector hc(10000, 100);
    std::vector<RankedHit> expRh;
    // produce expected result in normal order
    const size_t numHits = 150;
    for (uint32_t i = 0; i < numHits; ++i) {
        expRh.emplace_back();
        expRh.back()._docId = i;
        expRh.back()._rankValue = (i < 50) ? default_rank_value : (i + 100);
    }
    for (uint32_t i = 50; i < 150; ++i) {
        hc.addHit(i, i + 100);
    }
    // only the overflowing doc is out of order
    for (uint32_t i = 0; i < 50; ++i) {
        hc.addHit(i, i + 100);
    }
    std::unique_ptr<ResultSet> rs = hc.getResultSet();
    checkResult(*rs, expRh);
    checkResult(*rs, nullptr);
}

TEST(HitCollectorTest, require_that_hits_can_be_added_out_of_order_only_after_passing_array_limit)
{
    HitCollector hc(10000, 100);
    std::vector<RankedHit> expRh;
    // produce expected result in normal order
    const size_t numHits = 150;
    for (uint32_t i = 0; i < numHits; ++i) {
        expRh.emplace_back();
        expRh.back()._docId = i;
        expRh.back()._rankValue = (i < 50) ? default_rank_value : (i + 100);
    }
    // add results in reverse order
    const uint32_t numInOrder = numHits - 30;
    for (uint32_t i = 0; i < numInOrder; i++) {
        hc.addHit(i, i + 100);
    }
    for (uint32_t i = numHits; i-- > numInOrder; ) {
        hc.addHit(i, i + 100);
    }
    std::unique_ptr<ResultSet> rs = hc.getResultSet();
    checkResult(*rs, expRh);
    checkResult(*rs, nullptr);
}

struct RankDropFixture {
    uint32_t              _docid_limit;
    HitCollector          _hc;
    std::vector<uint32_t> _dropped;
    RankDropFixture(uint32_t docid_limit, uint32_t max_hits_size)
        : _docid_limit(docid_limit),
          _hc(docid_limit, max_hits_size)
    {
    }
    ~RankDropFixture();
    void add(std::vector<RankedHit> hits) {
        for (const auto& hit : hits) {
            _hc.addHit(hit.getDocId(), hit.getRank());
        }
    }
    void rerank(ScoreMap score_map, size_t count) {
        PredefinedScorer scorer(score_map);
        EXPECT_EQ(count, do_reRank(scorer, _hc, count));
    }
    std::unique_ptr<BitVector> make_bv(DocidVector docids) {
        auto bv = BitVector::create(_docid_limit);
        for (auto& docid : docids) {
            bv->setBit(docid);
        }
        return bv;
    }

    void setup() {
        // Initial 7 hits from first phase
        add({{5, 1100},{10, 1200},{11, 1300},{12, 1400},{14, 500},{15, 900},{16,1000}});
        // Rerank two best hits, calculate old and new ranges for reranked
        // hits that will cause hits not reranked to later be rescored by
        // dividing by 100.
        rerank({{11,14},{12,13}}, 2);
    }
    void check_result(std::optional<double> rank_drop_limit, RankedHitVector exp_array,
                      std::unique_ptr<BitVector> exp_bv, DocidVector exp_dropped) {
        auto rs = _hc.get_result_set(rank_drop_limit, &_dropped);
        checkResult(*rs, exp_array);
        checkResult(*rs, exp_bv.get());
        EXPECT_EQ(exp_dropped, _dropped);
    }
};

RankDropFixture::~RankDropFixture() = default;

TEST(HitCollectorTest, require_that_second_phase_rank_drop_limit_is_enforced)
{
    // Track rank score for all 7 hits from first phase
    RankDropFixture f(10000, 10);
    f.setup();
    f.check_result(9.0, {{5,11},{10,12},{11,14},{12,13},{16,10}},
                   {}, {14, 15});
}

TEST(HitCollectorTest, require_that_second_phase_rank_drop_limit_is_enforced_when_docid_vector_is_used)
{
    // Track rank score for 4 best hits from first phase, overflow to docid vector
    RankDropFixture f(10000, 4);
    f.setup();
    f.check_result(13.0, {{11,14}},
                   {}, {5,10,12,14,15,16});
}

TEST(HitCollectorTest, require_that_bitvector_is_not_dropped_without_second_phase_rank_drop_limit)
{
    // Track rank score for 4 best hits from first phase, overflow to bitvector
    RankDropFixture f(20, 4);
    f.setup();
    f.check_result(std::nullopt, {{5,11},{10,12},{11,14},{12,13}},
                   f.make_bv({5,10,11,12,14,15,16}), {});
}

TEST(HitCollectorTest, require_that_bitvector_is_dropped_with_second_phase_rank_drop_limit)
{
    // Track rank for 4 best hits from first phase, overflow to bitvector
    RankDropFixture f(20, 4);
    f.setup();
    f.check_result(9.0, {{5,11},{10,12},{11,14},{12,13}},
                   {}, {14,15,16});
}

GTEST_MAIN_RUN_ALL_TESTS()
