// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
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
#include <random>

#include <vespa/log/log.h>
LOG_SETUP("multibitvectoriterator_test");

using namespace search::queryeval;
using namespace search::fef;
using namespace search;
using vespalib::Trinary;

//-----------------------------------------------------------------------------

class Test : public vespalib::TestApp
{
public:
    Test();
    ~Test();
    void testAndNot();
    void testAnd();
    void testBug7163266();
    void testOr();
    void testAndWith(bool invert);
    void testEndGuard(bool invert);
    void testIteratorConformance();
    template<typename T>
    void testThatOptimizePreservesUnpack();
    template <typename T>
    void testOptimizeCommon(bool isAnd, bool invert);
    template <typename T>
    void testOptimizeAndOr(bool invert);
    template <typename T>
    void testSearch(bool strict, bool invert);
    int Main() override;
private:
    void verifySelectiveUnpack(SearchIterator & s, const TermFieldMatchData * tfmd);
    void searchAndCompare(SearchIterator::UP s, uint32_t docIdLimit);
    void setup();
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
            }
        }
    }
    std::vector< BitVector::UP > _bvs;
    std::vector< BitVector::UP > _bvs_inverted;
};

Test::Test() = default;
Test::~Test() = default;

void Test::setup()
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

typedef std::vector<uint32_t> H;

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
Test::testAndWith(bool invert)
{
    TermFieldMatchData tfmd;
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
    
        SearchIterator::UP s(AndSearch::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));

        s->initFullRange();
        H firstHits2 = seekNoReset(*s, 1, 130);
        SearchIterator::UP filter(s->andWith(createIter(2, invert, tfmd, false), 9));
        H lastHits2F = seekNoReset(*s, 130, _bvs[0]->size());
        
        children.clear();
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
        children.push_back(createIter(2, invert, tfmd, false).release());
        s.reset(AndSearch::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        s->initFullRange();
        H firstHits3 = seekNoReset(*s, 1, 130);
        H lastHits3 = seekNoReset(*s, 130, _bvs[0]->size());
        //These constants will change if rnd(341) is changed.
        EXPECT_EQUAL(30u, firstHits2.size());
        EXPECT_EQUAL(19u, firstHits3.size());
        EXPECT_EQUAL(1234u, lastHits2F.size());
        ASSERT_EQUAL(lastHits3.size(), lastHits2F.size());
        for (size_t i(0); i < lastHits3.size(); i++) {
            EXPECT_EQUAL(lastHits3[i], lastHits2F[i]);
        }
    }
}

void
Test::testAndNot()
{
    for (bool invert : {false, true}) {
        testOptimizeCommon<AndNotSearch>(false, invert);
        testSearch<AndNotSearch>(false, invert);
        testSearch<AndNotSearch>(true, invert);
    }
}

void
Test::testAnd()
{
    for (bool invert : {false, true}) {
        testOptimizeCommon<AndSearch>(true, invert);
        testOptimizeAndOr<AndSearch>(invert);
        testSearch<AndSearch>(false, invert);
        testSearch<AndSearch>(true, invert);
    }
}

void
Test::testOr()
{
    for (bool invert : {false, true}) {
        testOptimizeCommon< OrSearch >(false, invert);
        testOptimizeAndOr< OrSearch >(invert);
        testSearch<OrSearch>(false, invert);
        testSearch<OrSearch>(true, invert);
    }
}

void
Test::testBug7163266()
{
    TermFieldMatchData tfmd[30];
    _bvs[0]->setBit(1);
    _bvs[1]->setBit(1);
    MultiSearch::Children children;
    UnpackInfo unpackInfo;
    for (size_t i(0); i < 28; i++) {
        children.push_back(new TrueSearch(tfmd[2]));
        unpackInfo.add(i);
    }
    children.push_back(createIter(0, false, tfmd[0], false).release());
    children.push_back(createIter(1, false, tfmd[1], false).release());
    SearchIterator::UP s(AndSearch::create(children, false, unpackInfo));
    const MultiSearch * ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQUAL(30u, ms->getChildren().size());
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::SelectiveUnpack>", s->getClassName());
    for (size_t i(0); i < 28; i++) {
        EXPECT_TRUE(ms->needUnpack(i));
    }
    EXPECT_FALSE(ms->needUnpack(28));
    EXPECT_FALSE(ms->needUnpack(29));
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQUAL(29u, ms->getChildren().size());
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::SelectiveUnpack>", s->getClassName());
    for (size_t i(0); i < 28; i++) {
        EXPECT_TRUE(ms->needUnpack(i));
    }
    EXPECT_TRUE(ms->needUnpack(28)); // NB: force unpack all
    fixup_bitvectors();
}

template<typename T>
void
Test::testThatOptimizePreservesUnpack()
{
    TermFieldMatchData tfmd[4];
    _bvs[0]->setBit(1);
    _bvs[1]->setBit(1);
    _bvs[2]->setBit(1);
    MultiSearch::Children children;
    children.push_back(createIter(0, false, tfmd[0], false).release());
    children.push_back(createIter(1, false, tfmd[1], false).release());
    children.push_back(new TrueSearch(tfmd[2]));
    children.push_back(createIter(2, false, tfmd[3], false).release());
    UnpackInfo unpackInfo;
    unpackInfo.add(1);
    unpackInfo.add(2);
    SearchIterator::UP s(T::create(children, false, unpackInfo));
    s->initFullRange();
    const MultiSearch * ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQUAL(4u, ms->getChildren().size());
    verifySelectiveUnpack(*s, tfmd);
    tfmd[1].resetOnlyDocId(0);
    tfmd[2].resetOnlyDocId(0);
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    s->initFullRange();
    ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != nullptr);
    EXPECT_EQUAL(2u, ms->getChildren().size());
    verifySelectiveUnpack(*s, tfmd);
    fixup_bitvectors();
}

void
Test::verifySelectiveUnpack(SearchIterator & s, const TermFieldMatchData * tfmd)
{
    s.seek(1);
    EXPECT_EQUAL(0u, tfmd[0].getDocId());
    EXPECT_EQUAL(0u, tfmd[1].getDocId());
    EXPECT_EQUAL(0u, tfmd[2].getDocId());
    EXPECT_EQUAL(0u, tfmd[3].getDocId());
    s.unpack(1);
    EXPECT_EQUAL(0u, tfmd[0].getDocId());
    EXPECT_EQUAL(1u, tfmd[1].getDocId());
    EXPECT_EQUAL(1u, tfmd[2].getDocId());
    EXPECT_EQUAL(0u, tfmd[3].getDocId());
}

void
Test::searchAndCompare(SearchIterator::UP s, uint32_t docIdLimit)
{
    H a = seek(*s, docIdLimit);
    SearchIterator * p = s.get();
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    if (s.get() != p) {
        H b = seek(*s, docIdLimit);
        EXPECT_FALSE(a.empty());
        EXPECT_EQUAL(a.size(), b.size());
        for (size_t i(0); i < a.size(); i++) {
            EXPECT_EQUAL(a[i], b[i]);
        }
    }
}

template <typename T>
void
Test::testSearch(bool strict, bool invert)
{
    TermFieldMatchData tfmd;
    uint32_t docIdLimit(_bvs[0]->size());
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, strict).release());
        SearchIterator::UP s(T::create(children, strict));
        searchAndCompare(std::move(s), docIdLimit);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, strict).release());
        children.push_back(createIter(1, invert, tfmd, strict).release());
        SearchIterator::UP s(T::create(children, strict));
        searchAndCompare(std::move(s), docIdLimit);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, strict).release());
        children.push_back(createIter(1, invert, tfmd, strict).release());
        children.push_back(createIter(2, invert, tfmd, strict).release());
        SearchIterator::UP s(T::create(children, strict));
        searchAndCompare(std::move(s), docIdLimit);
    }
}

template <typename T>
void
Test::testOptimizeCommon(bool isAnd, bool invert)
{
    TermFieldMatchData tfmd;

    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false).release());

        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(1u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[0]) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(new EmptySearch());

        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[0]) != nullptr);
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(new EmptySearch());
        children.push_back(createIter(0, invert, tfmd, false).release());

        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0]) != nullptr);
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[1]) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(new EmptySearch());
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0]) != nullptr);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[1]) != nullptr);
        EXPECT_TRUE(Trinary::False == m.getChildren()[1]->is_strict());
    }
    {
        MultiSearch::Children children;
        children.push_back(new EmptySearch());
        children.push_back(createIter(0, invert, tfmd, true).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0]) != nullptr);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[1]) != nullptr);
        EXPECT_TRUE(Trinary::True == m.getChildren()[1]->is_strict());
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        SearchIterator::UP filter(s->andWith(createIter(2, invert, tfmd, false), 9));

        if (isAnd) {
            EXPECT_TRUE(nullptr == filter.get());
        } else {
            EXPECT_FALSE(nullptr == filter.get());
        }

        children.clear();
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
        s.reset(T::create(children, true));
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
Test::testOptimizeAndOr(bool invert)
{
    TermFieldMatchData tfmd;

    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(s.get()) != nullptr);
        EXPECT_TRUE(Trinary::False == s->is_strict());
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(new EmptySearch());
        children.push_back(createIter(1, invert, tfmd, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0]) != nullptr);
        EXPECT_TRUE(Trinary::False == m.getChildren()[0]->is_strict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, false).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
        children.push_back(new EmptySearch());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0]) != nullptr);
        EXPECT_TRUE(Trinary::False == m.getChildren()[0]->is_strict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, true).release());
        children.push_back(new EmptySearch());
        children.push_back(createIter(1, invert, tfmd, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0]) != nullptr);
        EXPECT_TRUE(Trinary::True == m.getChildren()[0]->is_strict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != nullptr);
    }
    {
        MultiSearch::Children children;
        children.push_back(createIter(0, invert, tfmd, true).release());
        children.push_back(createIter(1, invert, tfmd, false).release());
        children.push_back(new EmptySearch());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != nullptr);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0]) != nullptr);
        EXPECT_TRUE(Trinary::True == m.getChildren()[0]->is_strict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != nullptr);
    }
}

void
Test::testEndGuard(bool invert)
{
    typedef AndSearch T;
    TermFieldMatchData tfmd;

    MultiSearch::Children children;
    children.push_back(createIter(0, invert, tfmd, true).release());
    children.push_back(createIter(1, invert, tfmd, true).release());
    SearchIterator::UP s(T::create(children, false));
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    s->initFullRange();
    EXPECT_TRUE(s);
    EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(s.get()) != nullptr);
    MultiSearch & m(dynamic_cast<MultiSearch &>(*s));
    EXPECT_TRUE(m.seek(0) || !m.seek(0));
    EXPECT_TRUE(m.seek(3) || !m.seek(3));
    EXPECT_FALSE(m.seek(_bvs[0]->size()+987));
}

class Verifier : public search::test::SearchIteratorVerifier {
public:
    Verifier(size_t numBv, bool is_and);
    ~Verifier();

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
        bvs.push_back(BitVectorIterator::create(bv.get(), getDocIdLimit(), _tfmd, strict, false).release());
    }
    SearchIterator::UP iter(_is_and ? AndSearch::create(bvs, strict) : OrSearch::create(bvs, strict));
    auto mbvit = MultiBitVectorIteratorBase::optimize(std::move(iter));
    EXPECT_TRUE((bvs.size() < 2) || (dynamic_cast<const MultiBitVectorIteratorBase *>(mbvit.get()) != nullptr));
    EXPECT_EQUAL(strict, Trinary::True == mbvit->is_strict());
    return mbvit;
}

void Test::testIteratorConformance() {
    for (bool is_and : {false, true}) {
        for (size_t i(1); i < 6; i++) {
            Verifier searchIteratorVerifier(i, is_and);
            searchIteratorVerifier.verify();
        }
    }
}

int
Test::Main()
{
    TEST_INIT("multibitvectoriterator_test");
    setup();
    testBug7163266();
    testThatOptimizePreservesUnpack<OrSearch>();
    testThatOptimizePreservesUnpack<AndSearch>();
    TEST_FLUSH();
    testEndGuard(false);
    testEndGuard(true);
    TEST_FLUSH();
    testAndNot();
    TEST_FLUSH();
    testAnd();
    TEST_FLUSH();
    testOr();
    TEST_FLUSH();
    testAndWith(false);
    testAndWith(true);
    TEST_FLUSH();
    testIteratorConformance();
    TEST_FLUSH();
    TEST_DONE();
}

TEST_APPHOOK(Test);
