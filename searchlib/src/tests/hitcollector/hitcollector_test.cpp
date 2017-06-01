// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("hitcollector_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <iostream>

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/queryeval/hitcollector.h>
#include <vespa/searchlib/queryeval/scores.h>

using namespace search;
using namespace search::fef;
using namespace search::queryeval;

typedef std::map<uint32_t, feature_t> ScoreMap;

struct BasicScorer : public HitCollector::DocumentScorer
{
    feature_t _scoreDelta;
    BasicScorer(feature_t scoreDelta) : _scoreDelta(scoreDelta) {}
    virtual feature_t score(uint32_t docId) override {
        return docId + _scoreDelta;
    }
};

struct PredefinedScorer : public HitCollector::DocumentScorer
{
    ScoreMap _scores;
    PredefinedScorer(const ScoreMap &scores) : _scores(scores) {}
    virtual feature_t score(uint32_t docId) override {
        feature_t retval = 0.0;
        auto itr = _scores.find(docId);
        if (itr != _scores.end()) {
            retval = itr->second;
        }
        return retval;
    }
};

void checkResult(const ResultSet & rs, const std::vector<RankedHit> & exp)
{
    if (exp.size() > 0) {
        const RankedHit * rh = rs.getArray();
        ASSERT_TRUE(rh != NULL);
        ASSERT_EQUAL(rs.getArrayUsed(), exp.size());

        for (uint32_t i = 0; i < exp.size(); ++i) {
#if 0
            std::cout << " rh[" << i << "]._docId = " << rh[i]._docId << std::endl;
            std::cout << "exp[" << i << "]._docId = " << exp[i]._docId << std::endl;
            std::cout << " rh[" << i << "]._rankValue = " << rh[i]._rankValue << std::endl;
            std::cout << "exp[" << i << "]._rankValue = " << exp[i]._rankValue << std::endl;
#endif
            EXPECT_EQUAL(rh[i]._docId, exp[i]._docId);
            EXPECT_EQUAL(rh[i]._rankValue, exp[i]._rankValue);
        }
    } else {
        ASSERT_TRUE(rs.getArray() == NULL);
    }
}

void checkResult(ResultSet & rs, BitVector * exp)
{
    if (exp != NULL) {
        BitVector * bv = rs.getBitOverflow();
        ASSERT_TRUE(bv != NULL);
        bv->invalidateCachedCount();
        exp->invalidateCachedCount();
        LOG(info, "bv.hits: %u, exp.hits: %u", bv->countTrueBits(), exp->countTrueBits());
        ASSERT_TRUE(bv->countTrueBits() == exp->countTrueBits());
        EXPECT_TRUE(*bv == *exp);
    } else {
        ASSERT_TRUE(rs.getBitOverflow() == NULL);
    }
}

void testAddHit(uint32_t numDocs, uint32_t maxHitsSize, uint32_t maxHeapSize)
{

    LOG(info, "testAddHit: no hits");
    { // no hits
        HitCollector hc(numDocs, maxHitsSize, maxHeapSize);
        std::vector<RankedHit> expRh;

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        TEST_DO(checkResult(*rs.get(), expRh));
        TEST_DO(checkResult(*rs.get(), NULL));
    }

    LOG(info, "testAddHit: only ranked hits");
    { // only ranked hits
        HitCollector hc(numDocs, maxHitsSize, maxHeapSize);
        std::vector<RankedHit> expRh;

        for (uint32_t i = 0; i < maxHitsSize; ++i) {
            hc.addHit(i, i + 100);

            // build expected result set as we go along
            expRh.push_back(RankedHit());
            expRh.back()._docId = i;
            expRh.back()._rankValue = i + 100;
        }

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        TEST_DO(checkResult(*rs.get(), expRh));
        TEST_DO(checkResult(*rs.get(), NULL));
    }

    LOG(info, "testAddHit: both ranked hits and bit vector hits");
    { // both ranked hits and bit vector hits
        HitCollector hc(numDocs, maxHitsSize, maxHeapSize);
        std::vector<RankedHit> expRh;
        BitVector::UP expBv(BitVector::create(numDocs));

        for (uint32_t i = 0; i < numDocs; ++i) {
            hc.addHit(i, i + 100);

            // build expected result set as we go along
            expBv->setBit(i);
            if (i >= (numDocs - maxHitsSize)) {
                expRh.push_back(RankedHit());
                expRh.back()._docId = i;
                expRh.back()._rankValue = i + 100;
            }
        }

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        TEST_DO(checkResult(*rs.get(), expRh));
        TEST_DO(checkResult(*rs.get(), expBv.get()));
    }
}

TEST("testAddHit") {
    TEST_DO(testAddHit(30, 10, 5));
    TEST_DO(testAddHit(30, 10, 0));
    TEST_DO(testAddHit(400, 10, 5)); // 400/32 = 12 which is bigger than 10.
    TEST_DO(testAddHit(400, 10, 0));
}

struct Fixture {
    HitCollector hc;
    BitVector::UP expBv;
    BasicScorer scorer;

    Fixture()
        : hc(20, 10, 5), expBv(BitVector::create(20)), scorer(200)
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
    size_t reRank() {
        return hc.reRank(scorer);
    }
    size_t reRank(size_t count) {
        return hc.reRank(scorer, count);
    }
};

struct AscendingScoreFixture : Fixture {
    AscendingScoreFixture() : Fixture() {}
    virtual HitRank calculateScore(uint32_t i) override {
        return i + 100;
    }
};

struct DescendingScoreFixture : Fixture {
    DescendingScoreFixture() : Fixture() {}
    virtual HitRank calculateScore(uint32_t i) override {
        return 100 - i;
    }
};

TEST_F("testReRank - empty", Fixture) {
    EXPECT_EQUAL(0u, f.reRank());
}

TEST_F("testReRank - ascending", AscendingScoreFixture)
{
    f.addHits();
    EXPECT_EQUAL(5u, f.reRank());

    std::vector<RankedHit> expRh;
    for (uint32_t i = 10; i < 20; ++i) {  // 10 last are the best
        expRh.push_back(RankedHit(i, f.calculateScore(i)));
        if (i >= 15) { // hits from heap (5 last)
            expRh.back()._rankValue = i + 200; // after reranking
        }
    }
    EXPECT_EQUAL(expRh.size(), 10u);

    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    TEST_DO(checkResult(*rs.get(), expRh));
    TEST_DO(checkResult(*rs.get(), f.expBv.get()));
}

TEST_F("testReRank - descending", DescendingScoreFixture)
{
    f.addHits();
    EXPECT_EQUAL(5u, f.reRank());

    std::vector<RankedHit> expRh;
    for (uint32_t i = 0; i < 10; ++i) {  // 10 first are the best
        expRh.push_back(RankedHit(i, f.calculateScore(i)));
        if (i < 5) { // hits from heap (5 first)
            expRh.back()._rankValue = i + 200; // after reranking
        }
    }
    EXPECT_EQUAL(expRh.size(), 10u);

    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    TEST_DO(checkResult(*rs.get(), expRh));
    TEST_DO(checkResult(*rs.get(), f.expBv.get()));
}

TEST_F("testReRank - partial", AscendingScoreFixture)
{
    f.addHits();
    EXPECT_EQUAL(3u, f.reRank(3));

    std::vector<RankedHit> expRh;
    for (uint32_t i = 10; i < 20; ++i) {  // 10 last are the best
        expRh.push_back(RankedHit(i, f.calculateScore(i)));
        if (i >= 17) { // hits from heap (3 last)
            expRh.back()._rankValue = i + 200; // after reranking
        }
    }
    EXPECT_EQUAL(expRh.size(), 10u);

    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    TEST_DO(checkResult(*rs.get(), expRh));
    TEST_DO(checkResult(*rs.get(), f.expBv.get()));
}

TEST_F("require that scores for 2nd phase candidates can be retrieved", DescendingScoreFixture)
{
    f.addHits();
    std::vector<feature_t> scores = f.hc.getSortedHeapScores();
    ASSERT_EQUAL(5u, scores.size());
    EXPECT_EQUAL(100, scores[0]);
    EXPECT_EQUAL(99, scores[1]);
    EXPECT_EQUAL(98, scores[2]);
    EXPECT_EQUAL(97, scores[3]);
    EXPECT_EQUAL(96, scores[4]);
}

TEST("require that score ranges can be read and set.") {
    std::pair<Scores, Scores> ranges =
        std::make_pair(Scores(1.0, 2.0), Scores(3.0, 4.0));
    HitCollector hc(20, 10, 5);
    hc.setRanges(ranges);
    EXPECT_EQUAL(ranges.first.low, hc.getRanges().first.low);
    EXPECT_EQUAL(ranges.first.high, hc.getRanges().first.high);
    EXPECT_EQUAL(ranges.second.low, hc.getRanges().second.low);
    EXPECT_EQUAL(ranges.second.high, hc.getRanges().second.high);
}

TEST("testNoHitsToReRank") {
    uint32_t numDocs = 20;
    uint32_t maxHitsSize = 10;

    LOG(info, "testNoMDHeap: test it");
    {
        HitCollector hc(numDocs, maxHitsSize, 0);
        std::vector<RankedHit> expRh;

        for (uint32_t i = 0; i < maxHitsSize; ++i) {
            hc.addHit(i, i + 100);

            // build expected result set as we go along
            expRh.push_back(RankedHit());
            expRh.back()._docId = i;
            expRh.back()._rankValue = i + 100;
        }

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        TEST_DO(checkResult(*rs.get(), expRh));
        TEST_DO(checkResult(*rs.get(), NULL));
    }
}

void testScaling(const std::vector<feature_t> &initScores,
                 const ScoreMap &finalScores,
                 const std::vector<RankedHit> &expected)
{
    HitCollector hc(5, 5, 2);

    // first phase ranking
    for (uint32_t i = 0; i < 5; ++i) {
        hc.addHit(i, initScores[i]);
    }

    PredefinedScorer scorer(finalScores);
    // perform second phase ranking
    EXPECT_EQUAL(2u, hc.reRank(scorer));

    // check results
    std::unique_ptr<ResultSet> rs = hc.getResultSet();
    TEST_DO(checkResult(*rs.get(), expected));
}

TEST("testScaling") {
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

    { // scale down and adjust down
        exp[0]._rankValue = 0;   // scaled
        exp[1]._rankValue = 100; // scaled
        exp[2]._rankValue = 200; // scaled
        exp[3]._rankValue = 300; // from heap
        exp[4]._rankValue = 400; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 300;
        finalScores[4] = 400;

        testScaling(initScores, finalScores, exp);
    }
    { // scale down and adjust up
        exp[0]._rankValue = 200; // scaled
        exp[1]._rankValue = 300; // scaled
        exp[2]._rankValue = 400; // scaled
        exp[3]._rankValue = 500; // from heap
        exp[4]._rankValue = 600; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 500;
        finalScores[4] = 600;

        testScaling(initScores, finalScores, exp);
    }
    { // scale up and adjust down

        exp[0]._rankValue = -500; // scaled (-500)
        exp[1]._rankValue = 750;  // scaled
        exp[2]._rankValue = 2000; // scaled
        exp[3]._rankValue = 3250; // from heap
        exp[4]._rankValue = 4500; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 3250;
        finalScores[4] = 4500;

        testScaling(initScores, finalScores, exp);
    }
    { // minimal scale (second phase range = 0 (4 - 4) -> 1)
        exp[0]._rankValue = 1; // scaled
        exp[1]._rankValue = 2; // scaled
        exp[2]._rankValue = 3; // scaled
        exp[3]._rankValue = 4; // from heap
        exp[4]._rankValue = 4; // from heap

        // second phase ranking scores
        ScoreMap finalScores;
        finalScores[3] = 4;
        finalScores[4] = 4;

        testScaling(initScores, finalScores, exp);
    }
    { // minimal scale (first phase range = 0 (4000 - 4000) -> 1)
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

        testScaling(is, finalScores, exp);
    }
}

TEST("testOnlyBitVector") {
    uint32_t numDocs = 20;
    LOG(info, "testOnlyBitVector: test it");
    {
        HitCollector hc(numDocs, 0, 0);
        BitVector::UP expBv(BitVector::create(numDocs));

        for (uint32_t i = 0; i < numDocs; i += 2) {
            hc.addHit(i, i + 100);
            // build expected result set as we go along
            expBv->setBit(i);
        }

        std::unique_ptr<ResultSet> rs = hc.getResultSet();
        std::vector<RankedHit> expRh;
        TEST_DO(checkResult(*rs.get(), expRh));  // no ranked hits
        TEST_DO(checkResult(*rs.get(), expBv.get())); // only bit vector
    }
}

struct MergeResultSetFixture {
    const uint32_t numDocs;
    const uint32_t maxHitsSize;
    const uint32_t maxHeapSize;
    HitCollector hc;
    MergeResultSetFixture()
        : numDocs(100), maxHitsSize(80), maxHeapSize(30), hc(numDocs * 32, maxHitsSize, maxHeapSize)
    {}
};

TEST_F("require that result set is merged correctly with first phase ranking",
        MergeResultSetFixture)
{
    std::vector<RankedHit> expRh;
    for (uint32_t i = 0; i < f.numDocs; ++i) {
        f.hc.addHit(i, i + 1000);

        // build expected result set
        expRh.push_back(RankedHit());
        expRh.back()._docId = i;
        // only the maxHitsSize best hits gets a score
        expRh.back()._rankValue = (i < f.numDocs - f.maxHitsSize) ? 0 : i + 1000;
    }
    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    TEST_DO(checkResult(*rs.get(), expRh));
}

void
addExpectedHitForMergeTest(const MergeResultSetFixture &f, std::vector<RankedHit> &expRh, uint32_t docId)
{
    expRh.push_back(RankedHit());
    expRh.back()._docId = docId;
    if (docId < f.numDocs - f.maxHitsSize) { // only the maxHitsSize best hits gets a score
        expRh.back()._rankValue = 0;
    } else if (docId < f.numDocs - f.maxHeapSize) { // only first phase ranking
        expRh.back()._rankValue = docId + 500; // adjusted with - 500
    } else { // second phase ranking on the maxHeapSize best hits
        expRh.back()._rankValue = docId + 500;
    }
}

TEST_F("require that result set is merged correctly with second phase ranking (document scorer)",
        MergeResultSetFixture)
{
    // with second phase ranking that triggers rescoring / scaling
    BasicScorer scorer(500); // second phase ranking setting score to docId + 500
    std::vector<RankedHit> expRh;
    for (uint32_t i = 0; i < f.numDocs; ++i) {
        f.hc.addHit(i, i + 1000);
        addExpectedHitForMergeTest(f, expRh, i);
    }
    EXPECT_EQUAL(f.maxHeapSize, f.hc.reRank(scorer));
    std::unique_ptr<ResultSet> rs = f.hc.getResultSet();
    TEST_DO(checkResult(*rs.get(), expRh));
}

TEST("require that hits can be added out of order") {
    HitCollector hc(1000, 100, 10);
    std::vector<RankedHit> expRh;
    // produce expected result in normal order
    for (uint32_t i = 0; i < 5; ++i) {
        expRh.push_back(RankedHit());
        expRh.back()._docId = i;
        expRh.back()._rankValue = i + 100;
    }
    // add results in reverse order
    for (uint32_t i = 5; i-- > 0; ) {
        hc.addHit(i, i + 100);
    }
    std::unique_ptr<ResultSet> rs = hc.getResultSet();
    TEST_DO(checkResult(*rs.get(), expRh));
    TEST_DO(checkResult(*rs.get(), nullptr));
}

TEST("require that hits can be added out of order when passing array limit") {
    HitCollector hc(10000, 100, 10);
    std::vector<RankedHit> expRh;
    // produce expected result in normal order
    const size_t numHits = 150;
    for (uint32_t i = 0; i < numHits; ++i) {
        expRh.push_back(RankedHit());
        expRh.back()._docId = i;
        expRh.back()._rankValue = (i < 50) ? 0 : (i + 100);
    }
    // add results in reverse order
    for (uint32_t i = numHits; i-- > 0; ) {
        hc.addHit(i, i + 100);
    }
    std::unique_ptr<ResultSet> rs = hc.getResultSet();
    TEST_DO(checkResult(*rs.get(), expRh));
    TEST_DO(checkResult(*rs.get(), nullptr));
}

TEST("require that hits can be added out of order only after passing array limit") {
    HitCollector hc(10000, 100, 10);
    std::vector<RankedHit> expRh;
    // produce expected result in normal order
    const size_t numHits = 150;
    for (uint32_t i = 0; i < numHits; ++i) {
        expRh.push_back(RankedHit());
        expRh.back()._docId = i;
        expRh.back()._rankValue = (i < 50) ? 0 : (i + 100);
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
    TEST_DO(checkResult(*rs.get(), expRh));
    TEST_DO(checkResult(*rs.get(), nullptr));
}

TEST_MAIN() { TEST_RUN_ALL(); }
