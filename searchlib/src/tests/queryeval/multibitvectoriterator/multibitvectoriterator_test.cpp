// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/queryeval/multibitvectoriterator.h>
#include <vespa/searchlib/queryeval/emptysearch.h>
#include <vespa/searchlib/queryeval/truesearch.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <random>

using namespace search::queryeval;
using namespace search::fef;
using namespace search;
using vespalib::Trinary;

struct Fixture {
    Fixture();
    void testAndWith(bool invert);
    void testEndGuard(bool invert);
    void verifyUnpackOfOr(const UnpackInfo & unpackInfo);
    template <typename T>
    void testOptimizeCommon(bool isAnd, bool invert);
    template <typename T>
    void testSearch(bool strict, bool invert);
    template <typename T>
    void testOptimizeAndOr(bool invert);
    template<typename T>
    void testThatOptimizePreservesUnpack();

    SearchIterator::UP createIter(size_t index, bool inverted, TermFieldMatchData & tfmd, bool strict) {
        return BitVectorIterator::create(getBV(index, inverted), tfmd, strict, inverted);
    }
    BitVector * getBV(size_t index, bool inverted) {
        return inverted ? _bvs_inverted[index].get() : _bvs[index].get();
    }
    void fixup_bitvectors() {
        // Restore from inverted bitvectors after tampering
        for (int i = 0; i < 3; ++i) {
            if (_bvs_inverted[i]->testBit(1)) {
                _bvs[i]->clearBit(1);
            } else {
                _bvs[i]->setBit(1);
            }
        }
    }
    std::vector< BitVector::UP > _bvs;
    std::vector< BitVector::UP > _bvs_inverted;
};

Fixture::Fixture()
    : _bvs(),
      _bvs_inverted()
{
    std::minstd_rand rnd(341);
    for(size_t i(0); i < 3; i++) {
        _bvs.push_back(BitVector::create(10000));
        BitVector & bv(*_bvs.back());
        for (size_t j(0); j < bv.size(); j++) {
            int r = rnd();
            if (r & 0x1) {
                bv.setBit(j);
            }
        }
        auto inverted = BitVector::create(bv);
        inverted->notSelf();
        _bvs_inverted.push_back(std::move(inverted));
    }
}

class MultiBitVectorIteratorTest : public ::testing::Test,
                                   protected Fixture
{
protected:
    MultiBitVectorIteratorTest();
    ~MultiBitVectorIteratorTest() override;
};

MultiBitVectorIteratorTest::MultiBitVectorIteratorTest() = default;

MultiBitVectorIteratorTest::~MultiBitVectorIteratorTest() = default;

using H = std::vector<uint32_t>;

H
seekNoReset(SearchIterator & s, uint32_t start, uint32_t docIdLimit)
{
    H h;
    for (uint32_t docId(start); docId < docIdLimit; ) {
        if (s.seek(docId)) {
            h.push_back(docId);
            docId++;
        } else {
            if (s.getDocId() > docId) {
                docId = s.getDocId();
            } else {
                docId++;
            }
        }
        //printf("docId = %u\n", docId);
    }
    return h;
}

H
seek(SearchIterator & s, uint32_t docIdLimit)
{
    s.initFullRange();
    return seekNoReset(s, 1, docIdLimit);
}

void
Fixture::testAndWith(bool invert)
{
    TermFieldMatchData tfmd;
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false));
        children.push_back(createIter(1, invert, tfmd, false));
    
        SearchIterator::UP s = AndSearch::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));

        s->initFullRange();
        H firstHits2 = seekNoReset(*s, 1, 130);
        SearchIterator::UP filter(s->andWith(createIter(2, invert, tfmd, false), 9));
        H lastHits2F = seekNoReset(*s, 130, _bvs[0]->size());
        
        children.clear();
        children.push_back(createIter(0, invert, tfmd, false));
        children.push_back(createIter(1, invert, tfmd, false));
        children.push_back(createIter(2, invert, tfmd, false));
        s = AndSearch::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        s->initFullRange();
        H firstHits3 = seekNoReset(*s, 1, 130);
        H lastHits3 = seekNoReset(*s, 130, _bvs[0]->size());
        //These constants will change if rnd(341) is changed.
        EXPECT_EQ(30u, firstHits2.size());
        EXPECT_EQ(19u, firstHits3.size());
        EXPECT_EQ(1234u, lastHits2F.size());
        ASSERT_EQ(lastHits3.size(), lastHits2F.size());
        for (size_t i(0); i < lastHits3.size(); i++) {
            EXPECT_EQ(lastHits3[i], lastHits2F[i]);
        }
    }
}

TEST_F(MultiBitVectorIteratorTest, test_and_not)
{
    for (bool invert : {false, true}) {
        testOptimizeCommon<AndNotSearch>(false, invert);
        testSearch<AndNotSearch>(false, invert);
        testSearch<AndNotSearch>(true, invert);
    }
}

TEST_F(MultiBitVectorIteratorTest, test_and)
{
    for (bool invert : {false, true}) {
        testOptimizeCommon<AndSearch>(true, invert);
        testOptimizeAndOr<AndSearch>(invert);
        testSearch<AndSearch>(false, invert);
        testSearch<AndSearch>(true, invert);
    }
}

TEST_F(MultiBitVectorIteratorTest, test_or)
{
    for (bool invert : {false, true}) {
        testOptimizeCommon< OrSearch >(false, invert);
        testOptimizeAndOr< OrSearch >(invert);
        testSearch<OrSearch>(false, invert);
        testSearch<OrSearch>(true, invert);
    }
}

TEST_F(MultiBitVectorIteratorTest, test_and_with)
{
    testAndWith(false);
    testAndWith(true);
}

TEST_F(MultiBitVectorIteratorTest, test_bug_7163266)
{
    TermFieldMatchData tfmd[30];
    _bvs[0]->setBit(1);
    _bvs[1]->setBit(1);
    MultiSearch::Children children;
    UnpackInfo unpackInfo;
    for (size_t i(0); i < 28; i++) {
        children.emplace_back(new TrueSearch(tfmd[2]));
        unpackInfo.add(i);
    }
    children.push_back(createIter(0, false, tfmd[0], false));
    children.push_back(createIter(1, false, tfmd[1], false));
    SearchIterator::UP s = AndSearch::create(std::move(children), false, unpackInfo);
    const auto * ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQ(30u, ms->getChildren().size());
    EXPECT_EQ("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::SelectiveUnpack>", s->getClassName());
    for (size_t i(0); i < 28; i++) {
        EXPECT_TRUE(ms->needUnpack(i));
    }
    EXPECT_FALSE(ms->needUnpack(28));
    EXPECT_FALSE(ms->needUnpack(29));
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQ(29u, ms->getChildren().size());
    EXPECT_EQ("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::SelectiveUnpack>", s->getClassName());
    for (size_t i(0); i < 28; i++) {
        EXPECT_TRUE(ms->needUnpack(i));
    }
    EXPECT_TRUE(ms->needUnpack(28)); // NB: force unpack all
}

void
verifySelectiveUnpack(SearchIterator & s, const TermFieldMatchData * tfmd)
{
    s.seek(1);
    EXPECT_TRUE(tfmd[0].has_ranking_data(0u));
    EXPECT_TRUE(tfmd[1].has_ranking_data(0u));
    EXPECT_TRUE(tfmd[2].has_ranking_data(0u));
    EXPECT_TRUE(tfmd[3].has_ranking_data(0u));
    s.unpack(1);
    EXPECT_TRUE(tfmd[0].has_ranking_data(0u));
    EXPECT_TRUE(tfmd[1].has_ranking_data(1u));
    EXPECT_TRUE(tfmd[2].has_ranking_data(1u));
    EXPECT_TRUE(tfmd[3].has_ranking_data(0u));
}

template<typename T>
void
Fixture::testThatOptimizePreservesUnpack()
{
    TermFieldMatchData tfmd[4];
    _bvs[0]->setBit(1);
    _bvs[1]->setBit(1);
    _bvs[2]->setBit(1);
    MultiSearch::Children children;
    children.push_back(createIter(0, false, tfmd[0], false));
    children.push_back(createIter(1, false, tfmd[1], false));
    children.emplace_back(new TrueSearch(tfmd[2]));
    children.push_back(createIter(2, false, tfmd[3], false));
    UnpackInfo unpackInfo;
    unpackInfo.add(1);
    unpackInfo.add(2);
    SearchIterator::UP s = T::create(std::move(children), false, unpackInfo);
    s->initFullRange();
    const auto * ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQ(4u, ms->getChildren().size());
    verifySelectiveUnpack(*s, tfmd);
    tfmd[1].resetOnlyDocId(0);
    tfmd[2].resetOnlyDocId(0);
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    s->initFullRange();
    ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQ(2u, ms->getChildren().size());
    verifySelectiveUnpack(*s, tfmd);
    fixup_bitvectors();
}

void verifyOrUnpack(SearchIterator & s, TermFieldMatchData tfmd[3]) {
    s.initFullRange();
    s.seek(1);
    for (size_t i = 0; i < 3; i++) {
        EXPECT_TRUE(tfmd[i].has_ranking_data(0u));
    }
    s.unpack(1);
    EXPECT_TRUE(tfmd[0].has_ranking_data(0u));
    EXPECT_TRUE(tfmd[1].has_ranking_data(1u));
    EXPECT_TRUE(tfmd[2].has_ranking_data(0u));
}

TEST_F(MultiBitVectorIteratorTest, test_unpack_of_Or)
{
    _bvs[0]->clearBit(1);
    _bvs[1]->setBit(1);
    _bvs[2]->clearBit(1);
    UnpackInfo all;
    all.forceAll();
    verifyUnpackOfOr(all);

    UnpackInfo unpackInfo;
    unpackInfo.add(1);
    unpackInfo.add(2);
    verifyUnpackOfOr(unpackInfo);
}

void
Fixture::verifyUnpackOfOr(const UnpackInfo &unpackInfo)
{
    TermFieldMatchData tfmdA[3];
    MultiSearch::Children children;
    children.push_back(createIter(0, false, tfmdA[0], false));
    children.push_back(createIter(1, false, tfmdA[1], false));
    children.push_back(createIter(2, false, tfmdA[2], false));
    SearchIterator::UP s = OrSearch::create(std::move(children), false, unpackInfo);
    verifyOrUnpack(*s, tfmdA);

    for (auto & tfmd : tfmdA) {
        tfmd.resetOnlyDocId(0);
    }

    const auto * ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQ(3u, ms->getChildren().size());

    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    s->initFullRange();
    ms = dynamic_cast<const MultiBitVectorIteratorBase *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQ(3u, ms->getChildren().size());
    verifyOrUnpack(*s, tfmdA);

}

void
searchAndCompare(SearchIterator::UP s, uint32_t docIdLimit)
{
    H a = seek(*s, docIdLimit);
    SearchIterator * p = s.get();
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    if (s.get() != p) {
        H b = seek(*s, docIdLimit);
        EXPECT_FALSE(a.empty());
        EXPECT_EQ(a.size(), b.size());
        for (size_t i(0); i < a.size(); i++) {
            EXPECT_EQ(a[i], b[i]);
        }
    }
}

template <typename T>
void
Fixture::testSearch(bool strict, bool invert)
{
    TermFieldMatchData tfmd;
    uint32_t docIdLimit(_bvs[0]->size());
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, strict));
        SearchIterator::UP s = T::create(std::move(children), strict);
        searchAndCompare(std::move(s), docIdLimit);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, strict));
        children.push_back(createIter(1, invert, tfmd, strict));
        SearchIterator::UP s = T::create(std::move(children), strict);
        searchAndCompare(std::move(s), docIdLimit);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, strict));
        children.push_back(createIter(1, invert, tfmd, strict));
        children.push_back(createIter(2, invert, tfmd, strict));
        SearchIterator::UP s = T::create(std::move(children), strict);
        searchAndCompare(std::move(s), docIdLimit);
    }
}

template <typename T>
void
Fixture::testOptimizeCommon(bool isAnd, bool invert)
{
    TermFieldMatchData tfmd;

    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false));

        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(1u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[0].get()) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false));
        children.emplace_back(new EmptySearch());

        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[0].get()) != nullptr);
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1].get()) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.emplace_back(new EmptySearch());
        children.push_back(createIter(0, invert, tfmd, false));

        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0].get()) != nullptr);
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[1].get()) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.emplace_back(new EmptySearch());
        children.push_back(createIter(0, invert, tfmd, false));
        children.push_back(createIter(1, invert, tfmd, false));
    
        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0].get()) != nullptr);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[1].get()) != nullptr);
        EXPECT_TRUE(Trinary::False == m.getChildren()[1]->is_strict());
    }
    {
        MultiSearch::Children children;
        children.emplace_back(new EmptySearch());
        children.push_back(createIter(0, invert, tfmd, true));
        children.push_back(createIter(1, invert, tfmd, false));
    
        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0].get()) != nullptr);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[1].get()) != nullptr);
        EXPECT_TRUE(Trinary::True == m.getChildren()[1]->is_strict());
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false));
        children.push_back(createIter(1, invert, tfmd, false));
    
        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        SearchIterator::UP filter(s->andWith(createIter(2, invert, tfmd, false), 9));

        if (isAnd) {
            EXPECT_TRUE(nullptr == filter.get());
        } else {
            EXPECT_FALSE(nullptr == filter.get());
        }

        children.clear();
        children.push_back(createIter(0, invert, tfmd, false));
        children.push_back(createIter(1, invert, tfmd, false));
        s = T::create(std::move(children), true);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        filter = s->andWith(createIter(2, invert, tfmd, false), 9);

        if (isAnd) {
            EXPECT_TRUE(nullptr == filter.get());
        } else {
            EXPECT_FALSE(nullptr == filter.get());
        }
    }
}

template <typename T>
void
Fixture::testOptimizeAndOr(bool invert)
{
    TermFieldMatchData tfmd;

    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false));
        children.push_back(createIter(1, invert, tfmd, false));
    
        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(s.get()) != nullptr);
        EXPECT_TRUE(Trinary::False == s->is_strict());
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false));
        children.emplace_back(new EmptySearch());
        children.push_back(createIter(1, invert, tfmd, false));
    
        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0].get()) != nullptr);
        EXPECT_TRUE(Trinary::False == m.getChildren()[0]->is_strict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1].get()) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false));
        children.push_back(createIter(1, invert, tfmd, false));
        children.emplace_back(new EmptySearch());
    
        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0].get()) != nullptr);
        EXPECT_TRUE(Trinary::False == m.getChildren()[0]->is_strict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1].get()) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, true));
        children.emplace_back(new EmptySearch());
        children.push_back(createIter(1, invert, tfmd, false));
    
        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0].get()) != nullptr);
        EXPECT_TRUE(Trinary::True == m.getChildren()[0]->is_strict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1].get()) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, true));
        children.push_back(createIter(1, invert, tfmd, false));
        children.emplace_back(new EmptySearch());
    
        SearchIterator::UP s = T::create(std::move(children), false);
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const auto & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQ(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0].get()) != nullptr);
        EXPECT_TRUE(Trinary::True == m.getChildren()[0]->is_strict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1].get()) != nullptr);
    }
}

void
Fixture::testEndGuard(bool invert)
{
    using T = AndSearch;
    TermFieldMatchData tfmd;

    MultiSearch::Children children;
    children.push_back(createIter(0, invert, tfmd, true));
    children.push_back(createIter(1, invert, tfmd, true));
    SearchIterator::UP s = T::create(std::move(children), false);
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    s->initFullRange();
    EXPECT_TRUE(s);
    EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(s.get()) != nullptr);
    auto & m(dynamic_cast<MultiSearch &>(*s));
    EXPECT_TRUE(m.seek(0) || !m.seek(0));
    EXPECT_TRUE(m.seek(3) || !m.seek(3));
    EXPECT_FALSE(m.seek(_bvs[0]->size()+987));
}

TEST_F(MultiBitVectorIteratorTest, test_end_guard)
{
    testEndGuard(false);
    testEndGuard(true);
}

TEST_F(MultiBitVectorIteratorTest, test_that_optimize_preserves_unpack)
{
    testThatOptimizePreservesUnpack<OrSearch>();
    testThatOptimizePreservesUnpack<AndSearch>();
}

SearchIterator::UP
createDual(Fixture & f, TermFieldMatchData & tfmd, int32_t docIdLimit) {
    MultiSearch::Children children;
    children.push_back(f.createIter(0, false, tfmd, true));
    children.push_back(f.createIter(1, false, tfmd, true));
    SearchIterator::UP s = AndSearch::create(std::move(children), true);
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    EXPECT_TRUE(s);
    if (docIdLimit < 0) {
        s->initFullRange();
    } else {
        s->initRange(1, docIdLimit);
    }
    return s;
}

void
countUntilEnd(SearchIterator & s) {
    uint32_t seekCount = 0;
    for (uint32_t docId = s.seekFirst(1); !s.isAtEnd(); docId = s.seekNext(docId+1)) {
        seekCount++;
    }
    EXPECT_EQ(2459u, seekCount);
}

void
countUntilDocId(SearchIterator & s) {
    uint32_t seekCount = 0;
    for (uint32_t docId = s.seekFirst(1), endId = s.getEndId(); docId < endId; docId = s.seekNext(docId+1)) {
        seekCount++;
    }
    EXPECT_EQ(2459u, seekCount);
}

TEST_F(MultiBitVectorIteratorTest, test_that_short_vectors_dont_spin_at_end)
{
    TermFieldMatchData tfmd;
    countUntilEnd(*createDual(*this, tfmd, _bvs[0]->size()));
    countUntilDocId(*createDual(*this, tfmd, _bvs[0]->size()));

    countUntilDocId(*createDual(*this, tfmd, _bvs[0]->size() + 1));
    countUntilEnd(*createDual(*this, tfmd, _bvs[0]->size() + 1));

    countUntilDocId(*createDual(*this, tfmd, -1));
    countUntilEnd(*createDual(*this, tfmd, -1));
}

class Verifier : public search::test::SearchIteratorVerifier {
public:
    Verifier(size_t numBv, bool is_and);
    ~Verifier() override;

    SearchIterator::UP create(bool strict) const override;

private:
    bool _is_and;
    mutable TermFieldMatchData _tfmd;
    std::vector<BitVector::UP> _bvs;
};

Verifier::Verifier(size_t numBv, bool is_and)
    : _is_and(is_and),
      _bvs()
{
    for (size_t i(0); i < numBv; i++) {
        _bvs.push_back(BitVector::create(getDocIdLimit()));
    }
    for (uint32_t docId: getExpectedDocIds()) {
        if (_is_and) {
            for (auto & bv : _bvs) {
                bv->setBit(docId);
            }
        } else {
            _bvs[docId%_bvs.size()]->setBit(docId);
        }
    }
}
Verifier::~Verifier() = default;

SearchIterator::UP
Verifier::create(bool strict) const {
    MultiSearch::Children bvs;
    for (const auto & bv : _bvs) {
        bvs.push_back(BitVectorIterator::create(bv.get(), getDocIdLimit(), _tfmd, strict, false));
    }
    SearchIterator::UP iter(_is_and ? AndSearch::create(std::move(bvs), strict) : OrSearch::create(std::move(bvs), strict));
    auto mbvit = MultiBitVectorIteratorBase::optimize(std::move(iter));
    EXPECT_TRUE((_bvs.size() < 2) || (dynamic_cast<const MultiBitVectorIteratorBase *>(mbvit.get()) != nullptr));
    EXPECT_EQ(strict, Trinary::True == mbvit->is_strict());
    return mbvit;
}

TEST_F(MultiBitVectorIteratorTest, test_iterator_conformance)
{
    for (bool is_and : {false, true}) {
        for (size_t i(1); i < 6; i++) {
            Verifier searchIteratorVerifier(i, is_and);
            searchIteratorVerifier.verify();
        }
    }
}

TEST(MultiBitVectorTest, test_id_ref_str) {
    TermFieldMatchData tfmd;
    auto bv = BitVector::create(1000);
    auto vec = [&](int id){
                   auto res = BitVectorIterator::create(bv.get(), tfmd, false, false);
                   res->set_id(id);
                   return res;
               };
    MultiSearch::Children list;
    list.push_back(AndSearch::create({vec(3), vec(5)}, false));
    list.back()->set_id(7);
    list.push_back(vec(2));
    list.push_back(std::make_unique<EmptySearch>());
    list.back()->set_id(8);
    list.push_back(vec(4));
    list.push_back(vec(6));
    SearchIterator::UP search = AndSearch::create(std::move(list), false);
    search->set_id(10);
    search = MultiBitVectorIteratorBase::optimize(std::move(search));
    std::vector<SearchIterator*> refs;
    search->transform_children([&refs](SearchIterator::UP s)->SearchIterator::UP{
                                   refs.push_back(s.get());
                                   return s;
                               });
    EXPECT_EQ(search->make_id_ref_str(), "[10]");
    ASSERT_EQ(refs.size(), 3);
    EXPECT_EQ(refs[0]->make_id_ref_str(), "[7,3,5]");
    EXPECT_EQ(refs[1]->make_id_ref_str(), "[2,4,6]");
    EXPECT_EQ(refs[2]->make_id_ref_str(), "[8]");
}

GTEST_MAIN_RUN_ALL_TESTS()
