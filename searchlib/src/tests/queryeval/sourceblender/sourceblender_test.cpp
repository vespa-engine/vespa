// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/sourceblendersearch.h>
#include <vespa/searchlib/queryeval/simplesearch.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/test/searchiteratorverifier.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::queryeval;
using namespace search;
using std::make_unique;
using search::fef::MatchData;

/**
 * Proxy search used to verify unpack pattern
 **/
class UnpackChecker : public SearchIterator
{
private:
    SearchIterator::UP _search;
    SimpleResult   _unpacked;

protected:
    void doSeek(uint32_t docid) override {
        _search->seek(docid);
        setDocId(_search->getDocId());
    }
    void doUnpack(uint32_t docid) override {
        _unpacked.addHit(docid);
        _search->unpack(docid);
    }

public:
    explicit UnpackChecker(SearchIterator *search) : _search(search), _unpacked() {}
    ~UnpackChecker() override;
    const SimpleResult &getUnpacked() const { return _unpacked; }
};

UnpackChecker::~UnpackChecker() = default;

class MySelector : public search::FixedSourceSelector
{
public:
    explicit MySelector(int defaultSource) : search::FixedSourceSelector(defaultSource, "fs") { }
    MySelector & set(Source s, uint32_t docId) {
        setSource(s, docId);
        return *this;
    }
};

//-----------------------------------------------------------------------------

TEST(SourceBlenderTest, test_strictness)
{
    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    for (uint32_t i = 0; i < 2; ++i) {
        bool strict = (i == 0);

        SimpleResult a;
        SimpleResult b;

        a.addHit(2).addHit(5).addHit(6).addHit(8);
        b.addHit(3).addHit(5).addHit(6).addHit(7);

        auto *sel = new MySelector(5);
        sel->set(2, 1).set(3, 2).set(5, 2).set(7, 1);

        auto *blend_b = new SourceBlenderBlueprint(*sel);
        auto a_b = std::make_unique<SimpleBlueprint>(a);
        auto b_b = std::make_unique<SimpleBlueprint>(b);
        a_b->setSourceId(1);
        b_b->setSourceId(2);
        blend_b->addChild(std::move(a_b));
        blend_b->addChild(std::move(b_b));
        Blueprint::UP bp(blend_b);
        bp->basic_plan(strict, 100);
        bp->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP search = bp->createSearch(*md);
        search->initFullRange();
        SearchIterator &blend = *search;

        EXPECT_TRUE(!blend.seek(1u));
        if (strict) {
            EXPECT_EQ(2u, blend.getDocId());
        } else {
            EXPECT_EQ(blend.beginId(), blend.getDocId());
        }
        EXPECT_TRUE(blend.seek(5));
        EXPECT_EQ(5u, blend.getDocId());
        EXPECT_TRUE(!blend.seek(6));
        if (strict) {
            EXPECT_TRUE(blend.isAtEnd());
        } else {
            EXPECT_EQ(5u, blend.getDocId());
        }
        delete sel;
    }
}

TEST(SourceBlenderTest, test_full_sourceblender_search)
{
    SimpleResult a;
    SimpleResult b;
    SimpleResult c;

    a.addHit(2).addHit(11).addHit(21).addHit(34);
    b.addHit(3).addHit(11).addHit(21).addHit(33);
    c.addHit(4).addHit(11).addHit(21).addHit(32);

    // these are all handed over to the blender
    auto *ua = new UnpackChecker(new SimpleSearch(a));
    auto *ub = new UnpackChecker(new SimpleSearch(b));
    auto *uc = new UnpackChecker(new SimpleSearch(c));
    auto sel = make_unique<MySelector>(5);

    sel->set(2, 1).set(3, 2).set(11, 2).set(21, 3).set(34, 1);
    SourceBlenderSearch::Children abc;
    abc.emplace_back(ua, 1);
    abc.emplace_back(ub, 2);
    abc.emplace_back(uc, 3);

    SearchIterator::UP blend(SourceBlenderSearch::create(sel->createIterator(), abc, true));
    SimpleResult result;
    result.search(*blend, 100);

    SimpleResult expect_result;
    expect_result.addHit(2).addHit(3).addHit(11).addHit(21).addHit(34);

    SimpleResult expect_unpacked_a;
    expect_unpacked_a.addHit(2).addHit(34);

    SimpleResult expect_unpacked_b;
    expect_unpacked_b.addHit(3).addHit(11);

    SimpleResult expect_unpacked_c;
    expect_unpacked_c.addHit(21);

    EXPECT_EQ(expect_result, result);
    EXPECT_EQ(expect_unpacked_a, ua->getUnpacked());
    EXPECT_EQ(expect_unpacked_b, ub->getUnpacked());
    EXPECT_EQ(expect_unpacked_c, uc->getUnpacked());
}

using search::test::SearchIteratorVerifier;

class Verifier : public SearchIteratorVerifier {
public:
    Verifier();
    ~Verifier() override;
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
Verifier::~Verifier() = default;

TEST(SourceBlenderTest, test_that_source_blender_iterator_adheres_to_search_terator_requirements)
{
    Verifier searchIteratorVerifier;
    searchIteratorVerifier.verify();
}

GTEST_MAIN_RUN_ALL_TESTS()
