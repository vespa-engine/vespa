// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("blueprint_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/vespalib/objects/visit.h>

using namespace search::queryeval;
using namespace search::fef;

class Test : public vespalib::TestApp
{
public:
    void testEmptyBlueprint();
    void testSimpleBlueprint();
    void testFakeBlueprint();
    int Main() override;
};

void
Test::testEmptyBlueprint()
{
    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    EmptyBlueprint empty(FieldSpecBase(1, 11));
    ASSERT_TRUE(empty.getState().numFields() == 1u);
    EXPECT_EQUAL(1u, empty.getState().field(0).getFieldId());
    EXPECT_EQUAL(11u, empty.getState().field(0).getHandle());

    empty.fetchPostings(true);
    SearchIterator::UP search = empty.createSearch(*md, true);

    SimpleResult res;
    res.search(*search);
    SimpleResult expect; // empty
    EXPECT_EQUAL(res, expect);
}

void
Test::testSimpleBlueprint()
{
    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    SimpleResult a;
    a.addHit(3).addHit(5).addHit(7);
    SimpleBlueprint simple(a);
    simple.tag("tag");
    EXPECT_EQUAL("tag", simple.tag());
    simple.fetchPostings(true);
    SearchIterator::UP search = simple.createSearch(*md, true);

    SimpleResult res;
    res.search(*search);
    SimpleResult expect;
    expect.addHit(3).addHit(5).addHit(7);
    EXPECT_EQUAL(res, expect);
}

void
Test::testFakeBlueprint()
{
    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    FakeResult fake;
    fake.doc(10).len(50).pos(2).pos(3)
        .doc(25).len(10).pos(5);

    uint32_t fieldId = 0;
    TermFieldHandle handle = 0;
    FakeBlueprint orig(FieldSpec("<field>", fieldId, handle), fake);

    orig.fetchPostings(true);
    SearchIterator::UP search = orig.createSearch(*md, true);
    search->initFullRange();
    EXPECT_TRUE(!search->seek(1u));
    EXPECT_EQUAL(10u, search->getDocId());
    {
        search->unpack(10u);
        TermFieldMatchData &data = *md->resolveTermField(handle);
        EXPECT_EQUAL(fieldId, data.getFieldId());
        EXPECT_EQUAL(10u, data.getDocId());
        EXPECT_EQUAL(10u, data.getDocId());
        FieldPositionsIterator itr = data.getIterator();
        EXPECT_EQUAL(50u, itr.getFieldLength());
        EXPECT_EQUAL(2u, itr.size());
        ASSERT_TRUE(itr.valid());
        EXPECT_EQUAL(2u, itr.getPosition());
        itr.next();
        ASSERT_TRUE(itr.valid());
        EXPECT_EQUAL(3u, itr.getPosition());
        itr.next();
        EXPECT_TRUE(!itr.valid());
    }
    EXPECT_TRUE(search->seek(25));
    EXPECT_EQUAL(25u, search->getDocId());
    {
        search->unpack(25u);
        TermFieldMatchData &data = *md->resolveTermField(handle);
        EXPECT_EQUAL(fieldId, data.getFieldId());
        EXPECT_EQUAL(25u, data.getDocId());
        FieldPositionsIterator itr = data.getIterator();
        EXPECT_EQUAL(10u, itr.getFieldLength());
        EXPECT_EQUAL(1u, itr.size());
        ASSERT_TRUE(itr.valid());
        EXPECT_EQUAL(5u, itr.getPosition());
        itr.next();
        EXPECT_TRUE(!itr.valid());
    }
    EXPECT_TRUE(!search->seek(50));
    EXPECT_TRUE(search->isAtEnd());
}

int
Test::Main()
{
    TEST_INIT("leaf_blueprints_test");
    testEmptyBlueprint();
    testSimpleBlueprint();
    testFakeBlueprint();
    TEST_DONE();
}

TEST_APPHOOK(Test);
