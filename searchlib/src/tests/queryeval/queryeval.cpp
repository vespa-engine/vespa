// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("query_eval_test");

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/test/initrange.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/queryeval/andnotsearch.h>
#include <vespa/searchlib/queryeval/andsearch.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/nearsearch.h>
#include <vespa/searchlib/queryeval/orsearch.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/simplesearch.h>
#include <vespa/searchlib/queryeval/ranksearch.h>
#include <vespa/searchlib/queryeval/truesearch.h>
#include <vespa/searchlib/queryeval/sourceblendersearch.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/attribute/singlenumericattribute.hpp>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/searchlib/queryeval/isourceselector.h>

#include <vespa/searchlib/fef/fef.h>

using namespace search::queryeval;
using search::BitVector;
using search::BitVectorIterator;
using search::attribute::SearchContextParams;
using search::fef::MatchData;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataArray;
using search::test::InitRangeVerifier;

//-----------------------------------------------------------------------------

template <typename T, typename V=std::vector<T> >
class Collect
{
private:
    V _data;

public:
    Collect &add(const T &t) {
        _data.push_back(t);
        return *this;
    }
    operator const V &() const { return _data; }
};

SearchIterator *simple(const std::string &tag) {
    return &((new SimpleSearch(SimpleResult()))->tag(tag));
}

Collect<SearchIterator*, MultiSearch::Children> search2(const std::string &t1, const std::string &t2) {
    return Collect<SearchIterator*, MultiSearch::Children>().add(simple(t1)).add(simple(t2));
}


class ISourceSelectorDummy : public ISourceSelector
{
    static SourceStore _sourceStoreDummy;

public:
    static std::unique_ptr<sourceselector::Iterator>
    makeDummyIterator()
    {
        return std::make_unique<sourceselector::Iterator>(_sourceStoreDummy);
    }
};

ISourceSelector::SourceStore ISourceSelectorDummy::_sourceStoreDummy("foo");

std::unique_ptr<sourceselector::Iterator> selector() {
    return ISourceSelectorDummy::makeDummyIterator();
}

//-----------------------------------------------------------------------------

void testMultiSearch(SearchIterator & search) {
    MultiSearch & ms = dynamic_cast<MultiSearch &>(search);
    ms.initRange(3, 309);
    EXPECT_EQUAL(2u, ms.getDocId());
    EXPECT_EQUAL(309u, ms.getEndId());
    for (const auto & child : ms.getChildren()) {
        EXPECT_EQUAL(2u, child->getDocId());
        EXPECT_EQUAL(309u, child->getEndId());
    }
}

TEST("test that OR.andWith is a NOOP") {
    TermFieldMatchData tfmd;
    MultiSearch::Children ch;
    ch.push_back(new TrueSearch(tfmd));
    ch.push_back(new TrueSearch(tfmd));
    SearchIterator::UP search(OrSearch::create(ch, true));
    SearchIterator::UP filter(new TrueSearch(tfmd));

    EXPECT_TRUE(nullptr != search->andWith(std::move(filter), 1).get());
}

TEST("test that non-strict AND.andWith is a NOOP") {
    TermFieldMatchData tfmd;
    MultiSearch::Children ch;
    ch.push_back(new TrueSearch(tfmd));
    ch.push_back(new TrueSearch(tfmd));
    SearchIterator::UP search(AndSearch::create(ch, false));
    SearchIterator::UP filter(new TrueSearch(tfmd));
    filter = search->andWith(std::move(filter), 8);
    EXPECT_TRUE(nullptr != filter.get());
}

TEST("test that strict AND.andWith steals filter and places it correctly based on estimate") {
    TermFieldMatchData tfmd;
    MultiSearch::Children ch;
    ch.push_back(new TrueSearch(tfmd));
    ch.push_back(new TrueSearch(tfmd));
    SearchIterator::UP search(AndSearch::create(ch, true));
    static_cast<AndSearch &>(*search).estimate(7);
    SearchIterator::UP filter(new TrueSearch(tfmd));
    SearchIterator * filterP = filter.get();

    EXPECT_TRUE(nullptr == search->andWith(std::move(filter), 8).get());
    const MultiSearch::Children & andChildren = static_cast<MultiSearch &>(*search).getChildren();
    EXPECT_EQUAL(3u, andChildren.size());
    EXPECT_EQUAL(ch[0], andChildren[0]);
    EXPECT_EQUAL(filterP, andChildren[1]);
    EXPECT_EQUAL(ch[1], andChildren[2]);

    SearchIterator::UP filter2(new TrueSearch(tfmd));
    SearchIterator * filter2P = filter2.get();
    EXPECT_TRUE(nullptr == search->andWith(std::move(filter2), 6).get());
    EXPECT_EQUAL(4u, andChildren.size());
    EXPECT_EQUAL(filter2P, andChildren[0]);
    EXPECT_EQUAL(ch[0], andChildren[1]);
    EXPECT_EQUAL(filterP, andChildren[2]);
    EXPECT_EQUAL(ch[1], andChildren[3]);
}

class NonStrictTrueSearch : public TrueSearch
{
public:
    NonStrictTrueSearch(TermFieldMatchData & tfmd) : TrueSearch(tfmd) { }
    Trinary is_strict() const override { return Trinary::False; }
};

TEST("test that strict AND.andWith does not place non-strict iterator first") {
    TermFieldMatchData tfmd;
    MultiSearch::Children ch;
    ch.push_back(new TrueSearch(tfmd));
    ch.push_back(new TrueSearch(tfmd));
    SearchIterator::UP search(AndSearch::create(ch, true));
    static_cast<AndSearch &>(*search).estimate(7);
    SearchIterator::UP filter(new NonStrictTrueSearch(tfmd));
    SearchIterator * filterP = filter.get();
    EXPECT_TRUE(nullptr == search->andWith(std::move(filter), 6).get());
    const MultiSearch::Children & andChildren = static_cast<MultiSearch &>(*search).getChildren();
    EXPECT_EQUAL(3u, andChildren.size());
    EXPECT_EQUAL(ch[0], andChildren[0]);
    EXPECT_EQUAL(filterP, andChildren[1]);
    EXPECT_EQUAL(ch[1], andChildren[2]);
}

TEST("test that strict rank search forwards to its greedy first child") {
    TermFieldMatchData tfmd;
    SearchIterator::UP search(
        RankSearch::create(
            Collect<SearchIterator*, MultiSearch::Children>()
                .add(AndSearch::create(search2("a", "b"), true))
                .add(new TrueSearch(tfmd)),
            true)
    );
    SearchIterator::UP filter(new TrueSearch(tfmd));
    EXPECT_TRUE(nullptr == search->andWith(std::move(filter), 8).get());
}

TEST("test that non-strict rank search does NOT forward to its greedy first child") {
    TermFieldMatchData tfmd;
    SearchIterator::UP search(
        RankSearch::create(
            Collect<SearchIterator*, MultiSearch::Children>()
                .add(AndSearch::create(search2("a", "b"), true))
                .add(new TrueSearch(tfmd)),
            false)
    );
    SearchIterator::UP filter(new TrueSearch(tfmd));
    EXPECT_TRUE(nullptr != search->andWith(std::move(filter), 8).get());
}

TEST("test that strict andnot search forwards to its greedy first child") {
    TermFieldMatchData tfmd;
    SearchIterator::UP search(
        AndNotSearch::create(
            Collect<SearchIterator*, MultiSearch::Children>()
                .add(AndSearch::create(search2("a", "b"), true))
                .add(new TrueSearch(tfmd)),
            true)
    );
    SearchIterator::UP filter(new TrueSearch(tfmd));
    EXPECT_TRUE(nullptr == search->andWith(std::move(filter), 8).get());
}

TEST("test that non-strict andnot search does NOT forward to its greedy first child") {
    TermFieldMatchData tfmd;
    SearchIterator::UP search(
        AndNotSearch::create(
            Collect<SearchIterator*, MultiSearch::Children>()
                .add(AndSearch::create(search2("a", "b"), true))
                .add(new TrueSearch(tfmd)),
            false)
    );
    SearchIterator::UP filter(new TrueSearch(tfmd));
    EXPECT_TRUE(nullptr != search->andWith(std::move(filter), 8).get());
}

TEST("testAnd") {
    SimpleResult a;
    SimpleResult b;
    a.addHit(5).addHit(10).addHit(16).addHit(30);
    b.addHit(3).addHit(5).addHit(17).addHit(30).addHit(52);

    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    AndBlueprint *and_b = new AndBlueprint();
    and_b->addChild(Blueprint::UP(new SimpleBlueprint(a)));
    and_b->addChild(Blueprint::UP(new SimpleBlueprint(b)));
    Blueprint::UP bp(and_b);
    bp->fetchPostings(true);
    SearchIterator::UP and_ab = bp->createSearch(*md, true);

    EXPECT_TRUE(dynamic_cast<const AndSearch *>(and_ab.get()) != nullptr);
    EXPECT_EQUAL(4u, dynamic_cast<AndSearch &>(*and_ab).estimate());
    SimpleResult res;
    res.search(*and_ab);
    SimpleResult expect;
    expect.addHit(5).addHit(30);

    EXPECT_EQUAL(res, expect);
}

TEST("mutisearch and initRange") {
}

TEST("testOr") {
    {
        SimpleResult a;
        SimpleResult b;
        a.addHit(5).addHit(10);
        b.addHit(5).addHit(17).addHit(30);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        OrBlueprint *or_b = new OrBlueprint();
        or_b->addChild(Blueprint::UP(new SimpleBlueprint(a)));
        or_b->addChild(Blueprint::UP(new SimpleBlueprint(b)));
        Blueprint::UP bp(or_b);
        bp->fetchPostings(true);
        SearchIterator::UP or_ab = bp->createSearch(*md, true);

        SimpleResult res;
        res.search(*or_ab);
        SimpleResult expect;
        expect.addHit(5).addHit(10).addHit(17).addHit(30);

        EXPECT_EQUAL(res, expect);
    }
    {
        TermFieldMatchData tfmd;
        MultiSearch::Children ch;
        ch.push_back(new TrueSearch(tfmd));
        ch.push_back(new TrueSearch(tfmd));
        ch.push_back(new TrueSearch(tfmd));
        SearchIterator::UP orSearch(OrSearch::create(ch, true));
        testMultiSearch(*orSearch);
    }
}

class TestInsertRemoveSearch : public MultiSearch
{
public:
    TestInsertRemoveSearch(const MultiSearch::Children & children) :
        MultiSearch(children),
        _accumRemove(0),
        _accumInsert(0)
    { }
    virtual void onRemove(size_t index) override { _accumRemove += index; }
    virtual void onInsert(size_t index) override { _accumInsert += index; }
    size_t _accumRemove;
    size_t _accumInsert;
private:
    virtual void doSeek(uint32_t docid) override { (void) docid; }
};

struct MultiSearchRemoveTest {
    static SearchIterator::UP remove(MultiSearch &ms, size_t idx) { return ms.remove(idx); }
};

TEST("testMultiSearch") {
    MultiSearch::Children children;
    children.push_back(new EmptySearch());
    children.push_back(new EmptySearch());
    children.push_back(new EmptySearch());
    TestInsertRemoveSearch ms(children);
    EXPECT_EQUAL(3u, ms.getChildren().size());
    EXPECT_EQUAL(children[0], ms.getChildren()[0]);
    EXPECT_EQUAL(children[1], ms.getChildren()[1]);
    EXPECT_EQUAL(children[2], ms.getChildren()[2]);
    EXPECT_EQUAL(0u, ms._accumInsert);
    EXPECT_EQUAL(0u, ms._accumRemove);

    EXPECT_EQUAL(children[1], MultiSearchRemoveTest::remove(ms, 1).get());
    EXPECT_EQUAL(2u, ms.getChildren().size());
    EXPECT_EQUAL(children[0], ms.getChildren()[0]);
    EXPECT_EQUAL(children[2], ms.getChildren()[1]);
    EXPECT_EQUAL(0u, ms._accumInsert);
    EXPECT_EQUAL(1u, ms._accumRemove);

    children.push_back(new EmptySearch());
    ms.insert(1, SearchIterator::UP(children.back()));
    EXPECT_EQUAL(3u, ms.getChildren().size());
    EXPECT_EQUAL(children[0], ms.getChildren()[0]);
    EXPECT_EQUAL(children[3], ms.getChildren()[1]);
    EXPECT_EQUAL(children[2], ms.getChildren()[2]);
    EXPECT_EQUAL(1u, ms._accumInsert);
    EXPECT_EQUAL(1u, ms._accumRemove);
}

class DummySingleValueBitNumericAttributeBlueprint : public SimpleLeafBlueprint
{
public:
    DummySingleValueBitNumericAttributeBlueprint(const SimpleResult & result) :
        SimpleLeafBlueprint(FieldSpecBaseList()),
        _a("a", search::GrowStrategy()),
        _sc(),
        _tfmd()
    {
        for (size_t i(0); i < result.getHitCount(); i++) {
            size_t docId(result.getHit(i));
            uint32_t curDoc(0);
            for (_a.addDoc(curDoc); curDoc < docId; _a.addDoc(curDoc));
            _a.update(docId, 1);
        }
        _a.commit();
        _sc = _a.getSearch(search::QueryTermSimple::UP(new search::QueryTermSimple("1", search::QueryTermSimple::WORD)),
                           SearchContextParams().useBitVector(true));
    }
    virtual SearchIterator::UP
    createLeafSearch(const TermFieldMatchDataArray &tfmda, bool strict) const override
    {
        (void) tfmda;
        return _sc->createIterator(&_tfmd, strict);
    }
private:
    search::SingleValueBitNumericAttribute     _a;
    search::AttributeVector::SearchContext::UP _sc;
    mutable TermFieldMatchData _tfmd;
};


TEST("testAndNot") {
    {
        SimpleResult a;
        SimpleResult b;
        a.addHit(5).addHit(10);
        b.addHit(5).addHit(17).addHit(30);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        AndNotBlueprint *andnot_b = new AndNotBlueprint();
        andnot_b->addChild(Blueprint::UP(new SimpleBlueprint(a)));
        andnot_b->addChild(Blueprint::UP(new SimpleBlueprint(b)));
        Blueprint::UP bp(andnot_b);
        bp->fetchPostings(true);
        SearchIterator::UP andnot_ab = bp->createSearch(*md, true);

        SimpleResult res;
        res.search(*andnot_ab);
        SimpleResult expect;
        expect.addHit(10);

        EXPECT_EQUAL(res, expect);
    }
    {
        SimpleResult a;
        SimpleResult b;
        a.addHit(1).addHit(5).addHit(10);
        b.addHit(5).addHit(17).addHit(30);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        AndNotBlueprint *andnot_b = new AndNotBlueprint();
        andnot_b->addChild(Blueprint::UP(new SimpleBlueprint(a)));
        andnot_b->addChild(Blueprint::UP(new DummySingleValueBitNumericAttributeBlueprint(b)));
        Blueprint::UP bp(andnot_b);
        bp->fetchPostings(true);
        SearchIterator::UP andnot_ab = bp->createSearch(*md, true);
        EXPECT_TRUE(dynamic_cast<const OptimizedAndNotForBlackListing *>(andnot_ab.get()) != NULL);

        SimpleResult res;
        res.search(*andnot_ab);
        SimpleResult expect;
        expect.addHit(1).addHit(10);

        EXPECT_EQUAL(res, expect);
    }
    {
        SimpleResult a;
        SimpleResult b;
        SimpleResult c;
        a.addHit(1).addHit(5).addHit(10);
        b.addHit(5).addHit(17).addHit(30);
        c.addHit(1).addHit(5).addHit(10).addHit(17).addHit(30);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        AndNotBlueprint *andnot_b = new AndNotBlueprint();
        andnot_b->addChild(Blueprint::UP(new SimpleBlueprint(a)));
        andnot_b->addChild(Blueprint::UP(new SimpleBlueprint(b)));

        AndBlueprint *and_b = new AndBlueprint();
        and_b->addChild(Blueprint::UP(new SimpleBlueprint(c)));
        and_b->addChild(Blueprint::UP(andnot_b));
        Blueprint::UP bp(and_b);
        bp->fetchPostings(true);
        SearchIterator::UP and_cab = bp->createSearch(*md, true);

        SimpleResult res;
        res.search(*and_cab);
        SimpleResult expect;
        expect.addHit(1).addHit(10);

        EXPECT_EQUAL(res, expect);
    }
    {
    }
}

TEST("testRank") {
    {
        SimpleResult a;
        SimpleResult b;
        a.addHit(5).addHit(10).addHit(16).addHit(30);
        b.addHit(3).addHit(5).addHit(17).addHit(30).addHit(52);

        MatchData::UP md(MatchData::makeTestInstance(100, 10));
        RankBlueprint *rank_b = new RankBlueprint();
        rank_b->addChild(Blueprint::UP(new SimpleBlueprint(a)));
        rank_b->addChild(Blueprint::UP(new SimpleBlueprint(b)));
        Blueprint::UP bp(rank_b);
        bp->fetchPostings(true);
        SearchIterator::UP rank_ab = bp->createSearch(*md, true);

        SimpleResult res;
        res.search(*rank_ab);
        SimpleResult expect;
        expect.addHit(5).addHit(10).addHit(16).addHit(30);

        EXPECT_EQUAL(res, expect);
    }
}

TEST("testDump") {
    typedef SourceBlenderSearch::Child Source;
    SearchIterator::UP search(
            AndSearch::create(
                    Collect<SearchIterator*, MultiSearch::Children>()
                    .add(AndNotSearch::create(search2("+", "-"), true))
                    .add(AndSearch::create(search2("and_a", "and_b"), true))
                    .add(new BooleanMatchIteratorWrapper(SearchIterator::UP(simple("wrapped")), TermFieldMatchDataArray()))
                    .add(new NearSearch(search2("near_a", "near_b"),
                                        TermFieldMatchDataArray(),
                                        5u, true))
                    .add(new ONearSearch(search2("onear_a", "onear_b"),
                                         TermFieldMatchDataArray(), 10, true))
                    .add(OrSearch::create(search2("or_a", "or_b"), false))
                    .add(RankSearch::create(search2("rank_a", "rank_b"),false))
                    .add(SourceBlenderSearch::create(selector(), Collect<Source, SourceBlenderSearch::Children>()
                                 .add(Source(simple("blend_a"), 2))
                                 .add(Source(simple("blend_b"), 4)), true))
                    , true));
    vespalib::string sas = search->asString();
    EXPECT_TRUE(sas.size() > 50);
    // fprintf(stderr, "%s", search->asString().c_str());
}

TEST("testFieldSpec") {
    EXPECT_EQUAL(8u, sizeof(FieldSpecBase));
    EXPECT_EQUAL(72u, sizeof(FieldSpec));
}


const size_t unpack_child_cnt = 500;
const size_t max_unpack_size = 31;
const size_t max_unpack_index = 255;

std::vector<size_t> vectorize(const UnpackInfo &unpack) {
    std::vector<size_t> list;
    unpack.each([&](size_t i){list.push_back(i);}, unpack_child_cnt);
    return list;
}

std::vector<size_t> fill_vector(size_t begin, size_t end) {
    std::vector<size_t> list;
    for (size_t i = begin; i < end; ++i) {
        list.push_back(i);
    }
    return list;
}

void verify_unpack(const UnpackInfo &unpack, const std::vector<size_t> &expect) {
    std::vector<size_t> actual = vectorize(unpack);
    EXPECT_EQUAL(unpack.empty(), expect.empty());
    EXPECT_EQUAL(unpack.unpackAll(), (expect.size() == unpack_child_cnt));
    EXPECT_EQUAL(expect, actual);
    size_t child_idx = 0;
    for (size_t next_unpack: expect) {
        while (child_idx < next_unpack) {
            EXPECT_FALSE(unpack.needUnpack(child_idx++));
        }
        EXPECT_TRUE(unpack.needUnpack(child_idx++));
    }
}

TEST("require that unpack info has expected memory footprint") {
    EXPECT_EQUAL(32u, sizeof(UnpackInfo));
}

TEST("require that unpack info starts out empty") {
    verify_unpack(UnpackInfo(), {});
}

TEST("require that unpack info force all unpacks all children") {
    verify_unpack(UnpackInfo().forceAll(), fill_vector(0, unpack_child_cnt));
}

TEST("require that adding a large index to unpack info forces unpack all") {
    UnpackInfo unpack;
    unpack.add(0);
    unpack.add(max_unpack_index);
    verify_unpack(unpack, {0, max_unpack_index});
    unpack.add(max_unpack_index + 1);
    verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
}

TEST("require that adding too many children to unpack info forces unpack all") {
    UnpackInfo unpack;
    std::vector<size_t> expect;
    for (size_t i = 0; i < max_unpack_size; ++i) {
        unpack.add(i);
        expect.push_back(i);
    }
    verify_unpack(unpack, expect);
    unpack.add(100);
    verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
}

TEST("require that adding normal unpack info indexes works") {
    UnpackInfo unpack;
    unpack.add(3).add(5).add(7).add(14).add(50);
    verify_unpack(unpack, {3,5,7,14,50});
}

TEST("require that adding unpack info indexes out of order works") {
    UnpackInfo unpack;
    unpack.add(5).add(3).add(7).add(50).add(14);
    verify_unpack(unpack, {3,5,7,14,50});
}

TEST("require that basic insert remove of unpack info works") {
    UnpackInfo unpack;
    unpack.insert(1).insert(3);
    verify_unpack(unpack, {1, 3});
    unpack.insert(0);
    verify_unpack(unpack, {0, 2, 4});
    unpack.insert(3);
    verify_unpack(unpack, {0, 2, 3, 5});
    unpack.remove(1);
    verify_unpack(unpack, {0, 1, 2, 4});
    unpack.remove(1);
    verify_unpack(unpack, {0, 1, 3});
    unpack.remove(1);
    verify_unpack(unpack, {0, 2});
    unpack.remove(2);
    verify_unpack(unpack, {0});
    unpack.remove(0);
    verify_unpack(unpack, {});
}

TEST("require that inserting too many indexs into unpack info forces unpack all") {
    for (bool unpack_inserted: {true, false}) {
        UnpackInfo unpack;
        for (size_t i = 0; i < max_unpack_size; ++i) {
            unpack.add(i);
        }
        EXPECT_FALSE(unpack.unpackAll());
        unpack.insert(0, unpack_inserted);
        if (unpack_inserted) {
            verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
        } else {
            verify_unpack(unpack, fill_vector(1, max_unpack_size + 1));
        }
    }
}

TEST("require that implicitly overflowing indexes during insert in unpack info forces unpack all") {
    for (bool unpack_inserted: {true, false}) {
        UnpackInfo unpack;
        unpack.insert(max_unpack_index);
        EXPECT_FALSE(unpack.unpackAll());
        unpack.insert(5, unpack_inserted);
        verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
    }
}

TEST("require that inserting a too high index into unpack info forces unpack all") {
    for (bool unpack_inserted: {true, false}) {
        UnpackInfo unpack;
        for (size_t i = 0; i < 10; ++i) {
            unpack.add(i);
        }
        EXPECT_FALSE(unpack.unpackAll());
        unpack.insert(max_unpack_index + 1, unpack_inserted);
        if (unpack_inserted) {
            verify_unpack(unpack, fill_vector(0, unpack_child_cnt));
        } else {
            verify_unpack(unpack, fill_vector(0, 10));
        }
    }
}

TEST("require that we can insert indexes into unpack info that we do not unpack") {
    UnpackInfo unpack;
    unpack.add(10).add(20).add(30);
    verify_unpack(unpack, {10, 20, 30});    
    unpack.insert(5, false).insert(15, false).insert(25, false).insert(35, false);
    verify_unpack(unpack, {11, 22, 33});    
}

TEST("testTrueSearch") {
    EXPECT_EQUAL(16u, sizeof(EmptySearch));
    EXPECT_EQUAL(24u, sizeof(TrueSearch));

    TermFieldMatchData tfmd;
    TrueSearch t(tfmd);
    EXPECT_EQUAL(0u, t.getDocId());
    EXPECT_EQUAL(0u, t.getEndId());
    t.initRange(7, 10);
    EXPECT_EQUAL(6u, t.getDocId());
    EXPECT_EQUAL(10u, t.getEndId());
    EXPECT_TRUE(t.seek(9));
    EXPECT_EQUAL(9u, t.getDocId());
    EXPECT_FALSE(t.isAtEnd());
    EXPECT_TRUE(t.seek(10));
    EXPECT_EQUAL(10u, t.getDocId());
    EXPECT_TRUE(t.isAtEnd());
    t.initRange(4, 14);
    EXPECT_EQUAL(3u, t.getDocId());
    EXPECT_EQUAL(14u, t.getEndId());
    EXPECT_FALSE(t.isAtEnd());
}

TEST("test InitRangeVerifier") {
    InitRangeVerifier ir;
    EXPECT_EQUAL(207u, ir.getDocIdLimit());
    EXPECT_EQUAL(41u, ir.getExpectedDocIds().size());
    auto inverted = InitRangeVerifier::invert(ir.getExpectedDocIds(), 300);
    size_t numInverted = 300 - 41 - 1;
    EXPECT_EQUAL(numInverted, inverted.size());
    EXPECT_EQUAL(2u, inverted[0]);
    EXPECT_EQUAL(299u, inverted[numInverted - 1]);
    ir.verify(*ir.createIterator(ir.getExpectedDocIds(), false));
    ir.verify(*ir.createIterator(ir.getExpectedDocIds(), true));
}

TEST("Test multisearch and andsearchstrict iterators adheres to initRange") {
    InitRangeVerifier ir;
    ir.verify( AndSearch::create({ ir.createIterator(ir.getExpectedDocIds(), false).release(),
                                   ir.createFullIterator().release() }, false));

    ir.verify( AndSearch::create({ ir.createIterator(ir.getExpectedDocIds(), true).release(),
                                   ir.createFullIterator().release() }, true));
}

TEST("Test andnotsearchstrict iterators adheres to initRange") {
    InitRangeVerifier ir;
   
    TEST_DO(ir.verify( AndNotSearch::create({ir.createIterator(ir.getExpectedDocIds(), false).release(),
                                             ir.createEmptyIterator().release() }, false)));
    TEST_DO(ir.verify( AndNotSearch::create({ir.createIterator(ir.getExpectedDocIds(), true).release(),
                                             ir.createEmptyIterator().release() }, true)));

    auto inverted = InitRangeVerifier::invert(ir.getExpectedDocIds(), ir.getDocIdLimit());
    TEST_DO(ir.verify( AndNotSearch::create({ir.createFullIterator().release(),
                                              ir.createIterator(inverted, false).release() }, false)));
    TEST_DO(ir.verify( AndNotSearch::create({ir.createFullIterator().release(),
                                              ir.createIterator(inverted, false).release() }, true)));
}


TEST_MAIN() { TEST_RUN_ALL(); }
