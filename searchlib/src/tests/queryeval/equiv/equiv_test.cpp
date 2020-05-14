// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("equiv_test");
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::queryeval;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::fef::FieldPositionsIterator;

class EquivTest : public ::testing::Test {
protected:
    EquivTest();
    ~EquivTest();

    void test_equiv(bool strict);
};

EquivTest::EquivTest() = default;

EquivTest::~EquivTest() = default;

void
EquivTest::test_equiv(bool strict)
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
    auto bp = std::make_unique<EquivBlueprint>(fields, subLayout);

    bp->addTerm(std::make_unique<FakeBlueprint>(FieldSpec("foo", 1, fbh11), a), 1.0);
    bp->addTerm(std::make_unique<FakeBlueprint>(FieldSpec("bar", 2, fbh21), b), 1.0);
    bp->addTerm(std::make_unique<FakeBlueprint>(FieldSpec("bar", 2, fbh22), c), 1.0);

    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    bp->fetchPostings(ExecuteInfo::create(strict));
    SearchIterator::UP search = bp->createSearch(*md, strict);
    search->initFullRange();

    EXPECT_TRUE(!search->seek(3));
    if (!strict) {
        EXPECT_EQ(SearchIterator::beginId(), search->getDocId());
        EXPECT_TRUE(search->seek(5u));
    }
    EXPECT_EQ(5u, search->getDocId());
    { // test doc 5 results
        search->unpack(5u);
        {
            TermFieldMatchData &data = *md->resolveTermField(1);
            EXPECT_EQ(1u, data.getFieldId());
            EXPECT_EQ(5u, data.getDocId());
            FieldPositionsIterator itr = data.getIterator();
            EXPECT_EQ(1u, itr.size());
            ASSERT_TRUE(itr.valid());
            EXPECT_EQ(1u, itr.getPosition());
            itr.next();
            EXPECT_TRUE(!itr.valid());
        }
        {
            TermFieldMatchData &data = *md->resolveTermField(2);
            EXPECT_EQ(2u, data.getFieldId());
            EXPECT_EQ(5u, data.getDocId());
            FieldPositionsIterator itr = data.getIterator();
            EXPECT_EQ(2u, itr.size());
            ASSERT_TRUE(itr.valid());
            EXPECT_EQ(2u, itr.getPosition());
            itr.next();
            ASSERT_TRUE(itr.valid());
            EXPECT_EQ(3u, itr.getPosition());
            itr.next();
            EXPECT_TRUE(!itr.valid());
        }
    }
    EXPECT_TRUE(!search->seek(7));
    if (!strict) {
        EXPECT_EQ(5u, search->getDocId());
        EXPECT_TRUE(search->seek(10u));
    }
    EXPECT_EQ(10u, search->getDocId());
    { // test doc 10 results
        search->unpack(10u);
        EXPECT_EQ(5u, md->resolveTermField(1)->getDocId()); // no match
        {
            TermFieldMatchData &data = *md->resolveTermField(2);
            EXPECT_EQ(2u, data.getFieldId());
            EXPECT_EQ(10u, data.getDocId());
            FieldPositionsIterator itr = data.getIterator();
            EXPECT_EQ(1u, itr.size());
            ASSERT_TRUE(itr.valid());
            EXPECT_EQ(4u, itr.getPosition());
            itr.next();
            EXPECT_TRUE(!itr.valid());
        }
    }
    EXPECT_TRUE(!search->seek(13));
    if (strict) {
        EXPECT_TRUE(search->isAtEnd());
    } else {
        EXPECT_EQ(10u, search->getDocId());
    }
}


TEST_F(EquivTest, nonstrict)
{
    test_equiv(false);
}

TEST_F(EquivTest, strict)
{
    test_equiv(true);
}

GTEST_MAIN_RUN_ALL_TESTS()
