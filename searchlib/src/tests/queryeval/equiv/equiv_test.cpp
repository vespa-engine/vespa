// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("equiv_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/fef/matchdatalayout.h>

using namespace search::queryeval;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::fef::FieldPositionsIterator;

class Test : public vespalib::TestApp {
public:
    void testEquiv();
    int Main() override;
};

void
Test::testEquiv()
{
    FakeResult a;
    FakeResult b;
    FakeResult c;

    a.doc(5).pos(1);
    b.doc(5).pos(2);
    c.doc(5).pos(3).doc(10).pos(4);

    MatchDataLayout subLayout;
    TermFieldHandle fbh11 = subLayout.allocTermField(1);
    TermFieldHandle fbh21 = subLayout.allocTermField(2);
    TermFieldHandle fbh22 = subLayout.allocTermField(2);

    FieldSpecBaseList fields;
    fields.add(FieldSpecBase(1, 1));
    fields.add(FieldSpecBase(2, 2));
    EquivBlueprint *eq_b = new EquivBlueprint(fields, subLayout);

    eq_b->addTerm(Blueprint::UP(new FakeBlueprint(FieldSpec("foo", 1, fbh11), a)), 1.0);
    eq_b->addTerm(Blueprint::UP(new FakeBlueprint(FieldSpec("bar", 2, fbh21), b)), 1.0);
    eq_b->addTerm(Blueprint::UP(new FakeBlueprint(FieldSpec("bar", 2, fbh22), c)), 1.0);

    Blueprint::UP bp(eq_b);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        TEST_STATE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->fetchPostings(ExecuteInfo::create(strict));
        SearchIterator::UP search = bp->createSearch(*md, strict);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (!strict) {
            EXPECT_EQUAL(SearchIterator::beginId(), search->getDocId());
            EXPECT_TRUE(search->seek(5u));
        }
        EXPECT_EQUAL(5u, search->getDocId());
        { // test doc 5 results
            search->unpack(5u);
            {
                TermFieldMatchData &data = *md->resolveTermField(1);
                EXPECT_EQUAL(1u, data.getFieldId());
                EXPECT_EQUAL(5u, data.getDocId());
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQUAL(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(1u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
            {
                TermFieldMatchData &data = *md->resolveTermField(2);
                EXPECT_EQUAL(2u, data.getFieldId());
                EXPECT_EQUAL(5u, data.getDocId());
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQUAL(2u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(2u, itr.getPosition());
                itr.next();
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(3u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(!search->seek(7));
        if (!strict) {
            EXPECT_EQUAL(5u, search->getDocId());
            EXPECT_TRUE(search->seek(10u));
        }
        EXPECT_EQUAL(10u, search->getDocId());
        { // test doc 10 results
            search->unpack(10u);
            EXPECT_EQUAL(5u, md->resolveTermField(1)->getDocId()); // no match
            {
                TermFieldMatchData &data = *md->resolveTermField(2);
                EXPECT_EQUAL(2u, data.getFieldId());
                EXPECT_EQUAL(10u, data.getDocId());
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQUAL(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(4u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(!search->seek(13));
        if (strict) {
            EXPECT_TRUE(search->isAtEnd());
        } else {
            EXPECT_EQUAL(10u, search->getDocId());
        }
    }
}

int
Test::Main()
{
    TEST_INIT("equiv_test");
    testEquiv();
    TEST_DONE();
}

TEST_APPHOOK(Test);
