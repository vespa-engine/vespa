// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("equiv_test");

using namespace search::queryeval;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::fef::FieldPositionsIterator;

class EquivTest : public ::testing::Test {
protected:
    EquivTest();
    ~EquivTest() override;

    void test_equiv(bool strict, bool unpack_normal_features, bool unpack_interleaved_features);
};

EquivTest::EquivTest() = default;

EquivTest::~EquivTest() = default;

void
EquivTest::test_equiv(bool strict, bool unpack_normal_features, bool unpack_interleaved_features)
{
    FakeResult a;
    FakeResult b;
    FakeResult c;

    a.doc(5).pos(1).len(30).field_length(30).num_occs(1);
    b.doc(5).pos(2).len(30).field_length(30).num_occs(1);
    c.doc(5).pos(3).len(30).field_length(30).num_occs(1).doc(10).pos(4).len(35).field_length(35).num_occs(1);

    MatchDataLayout subLayout;
    TermFieldHandle fbh11 = subLayout.allocTermField(1);
    TermFieldHandle fbh21 = subLayout.allocTermField(2);
    TermFieldHandle fbh22 = subLayout.allocTermField(2);

    FieldSpecBaseList fields;
    fields.add(FieldSpecBase(1, 1));
    fields.add(FieldSpecBase(2, 2));
    auto bp = std::make_unique<EquivBlueprint>(std::move(fields), std::move(subLayout));

    bp->addTerm(std::make_unique<FakeBlueprint>(FieldSpec("foo", 1, fbh11), a), 1.0);
    bp->addTerm(std::make_unique<FakeBlueprint>(FieldSpec("bar", 2, fbh21), b), 1.0);
    bp->addTerm(std::make_unique<FakeBlueprint>(FieldSpec("bar", 2, fbh22), c), 1.0);

    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    for (uint32_t field_id = 1; field_id <= 2; ++field_id) {
        TermFieldMatchData &data = *md->resolveTermField(field_id);
        data.setNeedNormalFeatures(unpack_normal_features);
        data.setNeedInterleavedFeatures(unpack_interleaved_features);
    }
    bp->basic_plan(strict, 100);
    bp->fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = bp->createSearch(*md);
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
            EXPECT_TRUE(data.has_ranking_data(5u));
            FieldPositionsIterator itr = data.getIterator();
            if (unpack_normal_features) {
                EXPECT_EQ(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(1u, itr.getPosition());
                itr.next();
            }
            EXPECT_TRUE(!itr.valid());
            if (unpack_interleaved_features) {
                EXPECT_EQ(1u, data.getNumOccs());
                EXPECT_EQ(30u, data.getFieldLength());
            } else {
                EXPECT_EQ(0u, data.getNumOccs());
                EXPECT_EQ(0u, data.getFieldLength());
            }
        }
        {
            TermFieldMatchData &data = *md->resolveTermField(2);
            EXPECT_EQ(2u, data.getFieldId());
            EXPECT_TRUE(data.has_ranking_data(5u));
            FieldPositionsIterator itr = data.getIterator();
            if (unpack_normal_features) {
                EXPECT_EQ(2u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(2u, itr.getPosition());
                itr.next();
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(3u, itr.getPosition());
                itr.next();
            }
            EXPECT_TRUE(!itr.valid());
            if (unpack_interleaved_features) {
                EXPECT_EQ(2u, data.getNumOccs());
                EXPECT_EQ(30u, data.getFieldLength());
            } else {
                EXPECT_EQ(0u, data.getNumOccs());
                EXPECT_EQ(0u, data.getFieldLength());
            }
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
        EXPECT_TRUE(md->resolveTermField(1)->has_ranking_data(5u)); // no match
        {
            TermFieldMatchData &data = *md->resolveTermField(2);
            EXPECT_EQ(2u, data.getFieldId());
            EXPECT_TRUE(data.has_ranking_data(10u));
            FieldPositionsIterator itr = data.getIterator();
            if (unpack_normal_features) {
                EXPECT_EQ(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(4u, itr.getPosition());
                itr.next();
            }
            EXPECT_TRUE(!itr.valid());
            if (unpack_interleaved_features) {
                EXPECT_EQ(1u, data.getNumOccs());
                EXPECT_EQ(35u, data.getFieldLength());
            } else {
                EXPECT_EQ(0u, data.getNumOccs());
                EXPECT_EQ(0u, data.getFieldLength());
            }
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
    test_equiv(false, true, false);
}

TEST_F(EquivTest, strict)
{
    test_equiv(true, true, false);
}

TEST_F(EquivTest, nonstrict_no_normal_no_interleaved)
{
    test_equiv(false, false, false);
}

TEST_F(EquivTest, strict_no_normal_no_interleaved)
{
    test_equiv(true, false, false);
}

TEST_F(EquivTest, nonstrict_no_normal_interleaved)
{
    test_equiv(false, false, true);
}

TEST_F(EquivTest, strict_no_normal_interleaved)
{
    test_equiv(true, false, true);
}

TEST_F(EquivTest, nonstrict_normal_interleaved)
{
    test_equiv(false, true, true);
}

TEST_F(EquivTest, strict_normal_interleaved)
{
    test_equiv(true, true, true);
}

GTEST_MAIN_RUN_ALL_TESTS()
