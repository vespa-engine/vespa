// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/wand/wand_parts.h>

using namespace search::queryeval;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

using Term = wand::Term;

struct TestIterator : public SearchIterator
{
    MinMaxPostingInfo  _info;
    int32_t            _termWeight;
    bool               _useInfo;
    TermFieldMatchData _tfmd;
    uint32_t           _unpackDocId;

    using UP = std::unique_ptr<TestIterator>;
    TestIterator(int32_t maxWeight, int32_t termWeight, bool useInfo)
        : _info(0, maxWeight),
          _termWeight(termWeight),
          _useInfo(useInfo),
          _unpackDocId(0)
    {}
    void doSeek(uint32_t docId) override {
        (void) docId;
    }
    void doUnpack(uint32_t docId) override {
        _unpackDocId = docId;
        _tfmd.appendPosition(TermFieldMatchDataPosition(0, 0, _termWeight, 1));
    }
    const PostingInfo *getPostingInfo() const override {
        return (_useInfo ? &_info : nullptr);
    }
    static UP create(int32_t maxWeight, int32_t termWeight, bool useInfo) {
        return std::make_unique<TestIterator>(maxWeight, termWeight, useInfo);
    }
};

TEST("require that DotProductScorer calculates max score")
{
    TestIterator::UP itr = TestIterator::create(10, 0, true);
    Term term(itr.get(), 5, 0);
    EXPECT_EQUAL(50, wand::DotProductScorer::calculateMaxScore(term));
}

TEST("require that DotProductScorer uses default max weight when not available in search iterator")
{
    TestIterator::UP itr = TestIterator::create(10, 0, false);
    Term term(itr.get(), 5, 0);
    int64_t exp = (int64_t)5 * std::numeric_limits<int32_t>::max();
    EXPECT_EQUAL(exp, wand::DotProductScorer::calculateMaxScore(term));
}

TEST("require that DotProductScorer calculates term score")
{
    TestIterator::UP itr = TestIterator::create(0, 7, false);
    Term term(itr.get(), 5, 0, &itr->_tfmd);
    EXPECT_EQUAL(35, wand::DotProductScorer::calculateScore(term, 11));
    EXPECT_EQUAL(11u, itr->_unpackDocId);
}

TEST("test bm25 idf scorer for wand")
{
    wand::Bm25TermFrequencyScorer scorer(1000000, 1.0);
    EXPECT_EQUAL(13410046, scorer.calculateMaxScore(1, 1));
    EXPECT_EQUAL(11464136, scorer.calculateMaxScore(10, 1));
    EXPECT_EQUAL(6907256,  scorer.calculateMaxScore(1000, 1));
    EXPECT_EQUAL(4605121,  scorer.calculateMaxScore(10000, 1));
    EXPECT_EQUAL(2302581,  scorer.calculateMaxScore(100000, 1));
    EXPECT_EQUAL(693147,   scorer.calculateMaxScore(500000, 1));
    EXPECT_EQUAL(105360,   scorer.calculateMaxScore(900000, 1));
    EXPECT_EQUAL(10050,    scorer.calculateMaxScore(990000, 1));
}

TEST("test limited range of bm25 idf scorer for wand")
{
    wand::Bm25TermFrequencyScorer scorer08(1000000, 0.8);
    wand::Bm25TermFrequencyScorer scorer10(1000000, 1.0);
    EXPECT_EQUAL(8207814,  scorer08.calculateMaxScore(1000, 1));
    EXPECT_EQUAL(2690049,  scorer08.calculateMaxScore(990000, 1));
    EXPECT_EQUAL(6907256,  scorer10.calculateMaxScore(1000, 1));
    EXPECT_EQUAL(10050,  scorer10.calculateMaxScore(990000, 1));
}

TEST_MAIN() { TEST_RUN_ALL(); }
