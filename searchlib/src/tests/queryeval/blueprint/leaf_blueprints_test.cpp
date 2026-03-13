// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::queryeval;
using namespace search::fef;

TEST(LeafBlueprintsTest, empty_blueprint)
{
    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    EmptyBlueprint empty(FieldSpecBase(1, 11));
    ASSERT_TRUE(empty.getState().numFields() == 1u);
    EXPECT_EQ(1u, empty.getState().field(0).getFieldId());
    EXPECT_EQ(11u, empty.getState().field(0).getHandle());

    empty.basic_plan(true, 100);
    empty.fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = empty.createSearch(*md);

    SimpleResult res;
    res.search(*search, 100);
    SimpleResult expect; // empty
    EXPECT_EQ(res, expect);
}

TEST(LeafBlueprintsTest, simple_blueprint)
{
    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    SimpleResult a;
    a.addHit(3).addHit(5).addHit(7);
    SimpleBlueprint simple(a);
    simple.tag("tag");
    EXPECT_EQ("tag", simple.tag());
    simple.basic_plan(true, 100);
    simple.fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = simple.createSearch(*md);

    SimpleResult res;
    res.search(*search, 100);
    SimpleResult expect;
    expect.addHit(3).addHit(5).addHit(7);
    EXPECT_EQ(res, expect);
}

TEST(LeafBlueprintsTest, fake_blueprint)
{
    MatchData::UP md(MatchData::makeTestInstance(100, 10));
    FakeResult fake;
    fake.doc(10).len(50).pos(2).pos(3)
        .doc(25).len(10).pos(5);

    uint32_t fieldId = 0;
    TermFieldHandle handle = 0;
    FakeBlueprint orig(FieldSpec("<field>", fieldId, handle), fake);

    orig.basic_plan(true, 100);
    orig.fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = orig.createSearch(*md);
    search->initFullRange();
    EXPECT_TRUE(!search->seek(1u));
    EXPECT_EQ(10u, search->getDocId());
    {
        search->unpack(10u);
        TermFieldMatchData &data = *md->resolveTermField(handle);
        EXPECT_EQ(fieldId, data.getFieldId());
        EXPECT_TRUE(data.has_ranking_data(10u));
        FieldPositionsIterator itr = data.getIterator();
        EXPECT_EQ(50u, itr.getFieldLength());
        EXPECT_EQ(2u, itr.size());
        ASSERT_TRUE(itr.valid());
        EXPECT_EQ(2u, itr.getPosition());
        itr.next();
        ASSERT_TRUE(itr.valid());
        EXPECT_EQ(3u, itr.getPosition());
        itr.next();
        EXPECT_TRUE(!itr.valid());
    }
    EXPECT_TRUE(search->seek(25));
    EXPECT_EQ(25u, search->getDocId());
    {
        search->unpack(25u);
        TermFieldMatchData &data = *md->resolveTermField(handle);
        EXPECT_EQ(fieldId, data.getFieldId());
        EXPECT_TRUE(data.has_ranking_data(25u));
        FieldPositionsIterator itr = data.getIterator();
        EXPECT_EQ(10u, itr.getFieldLength());
        EXPECT_EQ(1u, itr.size());
        ASSERT_TRUE(itr.valid());
        EXPECT_EQ(5u, itr.getPosition());
        itr.next();
        EXPECT_TRUE(!itr.valid());
    }
    EXPECT_TRUE(!search->seek(50));
    EXPECT_TRUE(search->isAtEnd());
}

GTEST_MAIN_RUN_ALL_TESTS()
