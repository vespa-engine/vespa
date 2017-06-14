// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("multibitvectoriterator_test");
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

using namespace search::queryeval;
using namespace search::fef;
using namespace search;

//-----------------------------------------------------------------------------

class Test : public vespalib::TestApp
{
public:
    void testAndNot();
    void testAnd();
    void testBug7163266();
    void testOr();
    void testAndWith();
    void testEndGuard();
    template<typename T>
    void testThatOptimizePreservesUnpack();
    template <typename T>
    void testOptimizeCommon(bool isAnd);
    template <typename T>
    void testOptimizeAndOr();
    template <typename T>
    void testSearch(bool strict);
    int Main() override;
private:
    void verifySelectiveUnpack(SearchIterator & s, const TermFieldMatchData * tfmd);
    void searchAndCompare(SearchIterator::UP s, uint32_t docIdLimit);
    void setup();
    std::vector< BitVector::UP > _bvs;
};

void Test::setup()
{
    srand(7);
    for(size_t i(0); i < 3; i++) {
        _bvs.push_back(BitVector::create(10000));
        BitVector & bv(*_bvs.back());
        for (size_t j(0); j < bv.size(); j++) {
            int r = rand();
            if (r & 0x1) {
                bv.setBit(j);
            }
        }
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
Test::testAndWith()
{
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&tfmd);
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
    
        SearchIterator::UP s(AndSearch::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));

        s->initFullRange();
        H firstHits2 = seekNoReset(*s, 1, 130);
        SearchIterator::UP filter(s->andWith(BitVectorIterator::create(_bvs[2].get(), tfmda, false), 9));
        H lastHits2F = seekNoReset(*s, 130, _bvs[0]->size());
        
        children.clear();
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
        children.push_back(BitVectorIterator::create(_bvs[2].get(), tfmda, false).release());
        s.reset(AndSearch::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        s->initFullRange();
        H firstHits3 = seekNoReset(*s, 1, 130);
        H lastHits3 = seekNoReset(*s, 130, _bvs[0]->size());
        //These constants will change if srand(7) is changed.
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
    testOptimizeCommon<AndNotSearch>(false);
    testSearch<AndNotSearch>(false);
    testSearch<AndNotSearch>(true);
}

void
Test::testAnd()
{
    testOptimizeCommon<AndSearch>(true);
    testOptimizeAndOr<AndSearch>();
    testSearch<AndSearch>(false);
    testSearch<AndSearch>(true);
}

void
Test::testOr()
{
    testOptimizeCommon< OrSearch >(false);
    testOptimizeAndOr< OrSearch >();
    testSearch<OrSearch>(false);
    testSearch<OrSearch>(true);
}

void
Test::testBug7163266()
{
    TermFieldMatchData tfmd[30];
    TermFieldMatchDataArray tfmda[30];
    for (size_t i(0); i < 30; i++) {
        tfmda[i].add(&tfmd[i]);
    }
    _bvs[0]->setBit(1);
    _bvs[1]->setBit(1);
    MultiSearch::Children children;
    UnpackInfo unpackInfo;
    for (size_t i(0); i < 28; i++) {
        children.push_back(new TrueSearch(tfmd[2]));
        unpackInfo.add(i);
    }
    children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda[0], false).release());
    children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda[1], false).release());
    SearchIterator::UP s(AndSearch::create(children, false, unpackInfo));
    const MultiSearch * ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != NULL);
    EXPECT_EQUAL(30u, ms->getChildren().size());
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::SelectiveUnpack>", s->getClassName());
    for (size_t i(0); i < 28; i++) {
        EXPECT_TRUE(ms->needUnpack(i));
    }
    EXPECT_FALSE(ms->needUnpack(28));
    EXPECT_FALSE(ms->needUnpack(29));
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != NULL);
    EXPECT_EQUAL(29u, ms->getChildren().size());
    EXPECT_EQUAL("search::queryeval::AndSearchNoStrict<search::queryeval::(anonymous namespace)::SelectiveUnpack>", s->getClassName());
    for (size_t i(0); i < 28; i++) {
        EXPECT_TRUE(ms->needUnpack(i));
    }
    EXPECT_TRUE(ms->needUnpack(28)); // NB: force unpack all
}

template<typename T>
void
Test::testThatOptimizePreservesUnpack()
{
    TermFieldMatchData tfmd[4];
    TermFieldMatchDataArray tfmda[4];
    for (size_t i(0); i < 4; i++) {
        tfmda[i].add(&tfmd[i]);
    }
    _bvs[0]->setBit(1);
    _bvs[1]->setBit(1);
    _bvs[2]->setBit(1);
    MultiSearch::Children children;
    children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda[0], false).release());
    children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda[1], false).release());
    children.push_back(new TrueSearch(tfmd[2]));
    children.push_back(BitVectorIterator::create(_bvs[2].get(), tfmda[3], false).release());
    UnpackInfo unpackInfo;
    unpackInfo.add(1);
    unpackInfo.add(2);
    SearchIterator::UP s(T::create(children, false, unpackInfo));
    s->initFullRange();
    const MultiSearch * ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != NULL);
    EXPECT_EQUAL(4u, ms->getChildren().size());
    verifySelectiveUnpack(*s, tfmd);
    tfmd[1].resetOnlyDocId(0);
    tfmd[2].resetOnlyDocId(0);
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    s->initFullRange();
    ms = dynamic_cast<const MultiSearch *>(s.get());
    EXPECT_TRUE(ms != NULL);
    EXPECT_EQUAL(2u, ms->getChildren().size());
    verifySelectiveUnpack(*s, tfmd);
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
Test::testSearch(bool strict)
{
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&tfmd);
    uint32_t docIdLimit(_bvs[0]->size());
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, strict).release());
        SearchIterator::UP s(T::create(children, strict));
        searchAndCompare(std::move(s), docIdLimit);
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, strict).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, strict).release());
        SearchIterator::UP s(T::create(children, strict));
        searchAndCompare(std::move(s), docIdLimit);
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, strict).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, strict).release());
        children.push_back(BitVectorIterator::create(_bvs[2].get(), tfmda, strict).release());
        SearchIterator::UP s(T::create(children, strict));
        searchAndCompare(std::move(s), docIdLimit);
    }
}

template <typename T>
void
Test::testOptimizeCommon(bool isAnd)
{
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&tfmd);

    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());

        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(1u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[0]) != NULL);
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(new EmptySearch());

        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[0]) != NULL);
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != NULL);
    }
    {
        MultiSearch::Children children;
        children.push_back(new EmptySearch());
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());

        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0]) != NULL);
        EXPECT_TRUE(dynamic_cast<const BitVectorIterator *>(m.getChildren()[1]) != NULL);
    }
    {
        MultiSearch::Children children;
        children.push_back(new EmptySearch());
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s.get() != NULL);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0]) != NULL);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[1]) != NULL);
        EXPECT_FALSE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[1])->isStrict());
    }
    {
        MultiSearch::Children children;
        children.push_back(new EmptySearch());
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, true).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s.get() != NULL);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[0]) != NULL);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[1]) != NULL);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[1])->isStrict());
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        SearchIterator::UP filter(s->andWith(BitVectorIterator::create(_bvs[2].get(), tfmda, false), 9));

        if (isAnd) {
            EXPECT_TRUE(nullptr == filter.get());
        } else {
            EXPECT_FALSE(nullptr == filter.get());
        }

        children.clear();
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
        s.reset(T::create(children, true));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        filter = s->andWith(BitVectorIterator::create(_bvs[2].get(), tfmda, false), 9);

        if (isAnd) {
            EXPECT_TRUE(nullptr == filter.get());
        } else {
            EXPECT_FALSE(nullptr == filter.get());
        }
    }
}

template <typename T>
void
Test::testOptimizeAndOr()
{
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&tfmd);

    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s.get() != NULL);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(s.get()) != NULL);
        EXPECT_FALSE(dynamic_cast<const MultiBitVectorIteratorBase *>(s.get())->isStrict());
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(new EmptySearch());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s.get() != NULL);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0]) != NULL);
        EXPECT_FALSE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0])->isStrict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != NULL);
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, false).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
        children.push_back(new EmptySearch());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s.get() != NULL);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0]) != NULL);
        EXPECT_FALSE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0])->isStrict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != NULL);
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, true).release());
        children.push_back(new EmptySearch());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s.get() != NULL);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0]) != NULL);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0])->isStrict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != NULL);
    }
    {
        MultiSearch::Children children;
        children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, true).release());
        children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, false).release());
        children.push_back(new EmptySearch());
    
        SearchIterator::UP s(T::create(children, false));
        s = MultiBitVectorIteratorBase::optimize(std::move(s));
        EXPECT_TRUE(s.get() != NULL);
        EXPECT_TRUE(dynamic_cast<const T *>(s.get()) != NULL);
        const MultiSearch & m(dynamic_cast<const MultiSearch &>(*s));
        EXPECT_EQUAL(2u, m.getChildren().size());
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0]) != NULL);
        EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(m.getChildren()[0])->isStrict());
        EXPECT_TRUE(dynamic_cast<const EmptySearch *>(m.getChildren()[1]) != NULL);
    }
}

void
Test::testEndGuard()
{
    typedef AndSearch T;
    TermFieldMatchData tfmd;
    TermFieldMatchDataArray tfmda;
    tfmda.add(&tfmd);

    MultiSearch::Children children;
    children.push_back(BitVectorIterator::create(_bvs[0].get(), tfmda, true).release());
    children.push_back(BitVectorIterator::create(_bvs[1].get(), tfmda, true).release());
    SearchIterator::UP s(T::create(children, false));
    s = MultiBitVectorIteratorBase::optimize(std::move(s));
    s->initFullRange();
    EXPECT_TRUE(s.get() != NULL);
    EXPECT_TRUE(dynamic_cast<const MultiBitVectorIteratorBase *>(s.get()) != NULL);
    MultiSearch & m(dynamic_cast<MultiSearch &>(*s));
    EXPECT_TRUE(m.seek(0) || !m.seek(0));
    EXPECT_TRUE(m.seek(3) || !m.seek(3));
    EXPECT_FALSE(m.seek(_bvs[0]->size()+987));
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
    testEndGuard();
    TEST_FLUSH();
    testAndNot();
    TEST_FLUSH();
    testAnd();
    TEST_FLUSH();
    testOr();
    TEST_FLUSH();
    testAndWith();
    TEST_FLUSH();
    TEST_DONE();
}

TEST_APPHOOK(Test);
