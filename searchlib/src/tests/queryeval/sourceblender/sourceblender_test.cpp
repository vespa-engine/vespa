// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/queryeval/sourceblendersearch.h>
#include <vespa/searchlib/queryeval/simplesearch.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/common/bitvectoriterator.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <vespa/searchlib/fef/matchdata.h>

using namespace search::queryeval;
using namespace search::fef;
using namespace search;
using std::make_unique;

/**
 * Proxy search used to verify unpack pattern
 **/
class UnpackChecker : public SearchIterator
{
private:
    SearchIterator::UP _search;
    SimpleResult   _unpacked;

protected:
    virtual void doSeek(uint32_t docid) override {
        _search->seek(docid);
        setDocId(_search->getDocId());
    }
    virtual void doUnpack(uint32_t docid) override {
        _unpacked.addHit(docid);
        _search->unpack(docid);
    }

public:
    UnpackChecker(SearchIterator *search) : _search(search), _unpacked() {}
    const SimpleResult &getUnpacked() const { return _unpacked; }
};

class MySelector : public search::FixedSourceSelector
{
public:
    MySelector(int defaultSource) : search::FixedSourceSelector(defaultSource, "fs") { }
    MySelector & set(Source s, uint32_t docId) {
        setSource(s, docId);
        return *this;
    }
};

//-----------------------------------------------------------------------------

TEST("test strictness") {
    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    for (uint32_t i = 0; i < 2; ++i) {
        bool strict = (i == 0);

        SimpleResult a;
        SimpleResult b;

        a.addHit(2).addHit(5).addHit(6).addHit(8);
        b.addHit(3).addHit(5).addHit(6).addHit(7);

        MySelector *sel = new MySelector(5);
        sel->set(2, 1).set(3, 2).set(5, 2).set(7, 1);

        SourceBlenderBlueprint *blend_b = new SourceBlenderBlueprint(*sel);
        Blueprint::UP a_b(new SimpleBlueprint(a));
        Blueprint::UP b_b(new SimpleBlueprint(b));
        a_b->setSourceId(1);
        b_b->setSourceId(2);
        blend_b->addChild(std::move(a_b));
        blend_b->addChild(std::move(b_b));
        Blueprint::UP bp(blend_b);
        bp->fetchPostings(ExecuteInfo::createForTest(strict));
        SearchIterator::UP search = bp->createSearch(*md, strict);
        search->initFullRange();
        SearchIterator &blend = *search;

        EXPECT_TRUE(!blend.seek(1u));
        if (strict) {
            EXPECT_EQUAL(2u, blend.getDocId());
        } else {
            EXPECT_EQUAL(blend.beginId(), blend.getDocId());
        }
        EXPECT_TRUE(blend.seek(5));
        EXPECT_EQUAL(5u, blend.getDocId());
        EXPECT_TRUE(!blend.seek(6));
        if (strict) {
            EXPECT_TRUE(blend.isAtEnd());
        } else {
            EXPECT_EQUAL(5u, blend.getDocId());
        }
        delete sel;
    }
}

TEST("test full sourceblender search") {
    SimpleResult a;
    SimpleResult b;
    SimpleResult c;

    a.addHit(2).addHit(11).addHit(21).addHit(34);
    b.addHit(3).addHit(11).addHit(21).addHit(33);
    c.addHit(4).addHit(11).addHit(21).addHit(32);

    // these are all handed over to the blender
    UnpackChecker *ua = new UnpackChecker(new SimpleSearch(a));
    UnpackChecker *ub = new UnpackChecker(new SimpleSearch(b));
    UnpackChecker *uc = new UnpackChecker(new SimpleSearch(c));
    auto sel = make_unique<MySelector>(5);

    sel->set(2, 1).set(3, 2).set(11, 2).set(21, 3).set(34, 1);
    SourceBlenderSearch::Children abc;
    abc.push_back(SourceBlenderSearch::Child(ua, 1));
    abc.push_back(SourceBlenderSearch::Child(ub, 2));
    abc.push_back(SourceBlenderSearch::Child(uc, 3));

    SearchIterator::UP blend(SourceBlenderSearch::create(sel->createIterator(), abc, true));
    SimpleResult result;
    result.search(*blend);

    SimpleResult expect_result;
    expect_result.addHit(2).addHit(3).addHit(11).addHit(21).addHit(34);

    SimpleResult expect_unpacked_a;
    expect_unpacked_a.addHit(2).addHit(34);

    SimpleResult expect_unpacked_b;
    expect_unpacked_b.addHit(3).addHit(11);

    SimpleResult expect_unpacked_c;
    expect_unpacked_c.addHit(21);

    EXPECT_EQUAL(expect_result, result);
    EXPECT_EQUAL(expect_unpacked_a, ua->getUnpacked());
    EXPECT_EQUAL(expect_unpacked_b, ub->getUnpacked());
    EXPECT_EQUAL(expect_unpacked_c, uc->getUnpacked());
}

using search::test::SearchIteratorVerifier;

class Verifier : public SearchIteratorVerifier {
public:
    Verifier();
    ~Verifier();
    SearchIterator::UP create(bool strict) const override {
        return SearchIterator::UP(SourceBlenderSearch::create(_selector.createIterator(),
                                                              createChildren(strict),
                                                              strict));
    }
private:
    SourceBlenderSearch::Children
    createChildren(bool strict) const {
        SourceBlenderSearch::Children children;
        for (size_t index(0); index < _indexes.size(); index++) {
            children.emplace_back(createIterator(_indexes[index], strict).release(), index);
        }
        return children;
    }
    std::vector<DocIds> _indexes;
    MySelector _selector;
};

Verifier::Verifier() :
    _indexes(3),
    _selector(getDocIdLimit())
{
    for (uint32_t docId : getExpectedDocIds()) {
        const size_t indexId = docId % _indexes.size();
        _selector.set(docId, indexId);
        _indexes[indexId].push_back(docId);
    }
}
Verifier::~Verifier() {}

TEST("Test that source blender iterator adheres to SearchIterator requirements") {
    Verifier searchIteratorVerifier;
    searchIteratorVerifier.verify();
}

TEST_MAIN() { TEST_RUN_ALL(); }
