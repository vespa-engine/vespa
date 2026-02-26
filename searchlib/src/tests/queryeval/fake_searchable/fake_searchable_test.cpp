// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>

using namespace search::queryeval;
using namespace search::query;
using namespace search::fef;

struct FakeSearchableTest : ::testing::Test {
    Weight w;
    FakeRequestContext req_ctx;
    FakeSearchable source;
    FakeSearchableTest() : w(100), req_ctx(), source() {}
};

TEST(FakeResultTest, require_that_fake_result_works) {
    EXPECT_EQ(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).pos(6).elem(6).doc(6),
              FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).pos(6).elem(6).doc(6));

    EXPECT_FALSE(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5) ==
                 FakeResult().doc(1).elem(5).len(15).weight(5).pos(5));

    EXPECT_FALSE(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5) ==
                 FakeResult().doc(5).elem(1).len(15).weight(5).pos(5));

    EXPECT_FALSE(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5) ==
                 FakeResult().doc(5).elem(5).len(19).weight(5).pos(5));

    EXPECT_FALSE(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5) ==
                 FakeResult().doc(5).elem(5).len(15).weight(1).pos(5));

    EXPECT_FALSE(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5) ==
                 FakeResult().doc(5).elem(5).len(15).weight(5).pos(1));

    EXPECT_FALSE(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5) ==
                 FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).doc(6));

    EXPECT_FALSE(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5) ==
                 FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).elem(6));

    EXPECT_FALSE(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5) ==
                 FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).pos(6));
}

TEST_F(FakeSearchableTest, require_that_term_search_works) {
    source.addResult("fieldfoo", "word1",
                     FakeResult().doc(5).elem(2).pos(3).elem(4).pos(5));

    SimpleStringTerm termNode("word1", "viewfoo", 1, w);

    FieldSpecList fields;
    search::fef::MatchDataLayout mdl;
    mdl.allocTermField(0);
    fields.add(FieldSpec("fieldfoo", 1, mdl.allocTermField(1)));
    auto bp = source.createBlueprint(req_ctx, fields, termNode, mdl);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        SCOPED_TRACE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->basic_plan(strict, 100);
        bp->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP search = bp->createSearch(*md);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (strict) {
            EXPECT_EQ(5u, search->getDocId());
        } else {
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
                EXPECT_EQ(2u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(2u, itr.getElementId());
                EXPECT_EQ(3u, itr.getPosition());
                itr.next();
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(4u, itr.getElementId());
                EXPECT_EQ(5u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(!search->seek(13));
        if (strict) {
            EXPECT_TRUE(search->isAtEnd());
        }
    }
}

TEST_F(FakeSearchableTest, require_that_phrase_search_works) {
    source.addResult("fieldfoo", "word1",
                     FakeResult().doc(3).pos(7).doc(5).pos(3));
    source.addResult("fieldfoo", "word2",
                     FakeResult().doc(2).pos(1).doc(3).pos(10).doc(5).pos(4));

    SimplePhrase phraseNode("viewfoo", 1, w);
    phraseNode.append(Node::UP(new SimpleStringTerm("word1", "viewfoo", 2, w)));
    phraseNode.append(Node::UP(new SimpleStringTerm("word2", "viewfoo", 3, w)));

    search::fef::MatchDataLayout mdl;
    mdl.allocTermField(0);
    FieldSpecList fields;
    fields.add(FieldSpec("fieldfoo", 1, mdl.allocTermField(1)));
    auto bp = source.createBlueprint(req_ctx, fields, phraseNode, mdl);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        SCOPED_TRACE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->basic_plan(strict, 100);
        bp->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP search = bp->createSearch(*md);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (strict) {
            EXPECT_EQ(5u, search->getDocId());
        } else {
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
                EXPECT_EQ(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(3u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(!search->seek(13));
        if (strict) {
            EXPECT_TRUE(search->isAtEnd());
        }
    }
}

TEST_F(FakeSearchableTest, require_that_weigheted_set_search_works) {
    source.addResult("fieldfoo", "friend1",
                     FakeResult().doc(3).doc(5).doc(7).doc(9));
    source.addResult("fieldfoo", "friend2",
                     FakeResult().doc(3).doc(4).doc(5).doc(6));
    source.addResult("fieldfoo", "friend3",
                     FakeResult().doc(5));

    SimpleWeightedSetTerm weightedSet(2, "fieldfoo", 1, w);
    weightedSet.addTerm("friend1", Weight(1));
    weightedSet.addTerm("friend2", Weight(2));

    FieldSpecList fields;
    search::fef::MatchDataLayout mdl;
    mdl.allocTermField(0);
    fields.add(FieldSpec("fieldfoo", 1, mdl.allocTermField(1)));
    auto bp = source.createBlueprint(req_ctx, fields, weightedSet, mdl);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        SCOPED_TRACE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->basic_plan(strict, 100);
        bp->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP search = bp->createSearch(*md);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(2));
        if (strict) {
            EXPECT_EQ(3u, search->getDocId());
        } else {
            EXPECT_TRUE(search->seek(3u));
        }
        EXPECT_EQ(3u, search->getDocId());
        { // test doc 3 results
            search->unpack(3u);
            {
                TermFieldMatchData &data = *md->resolveTermField(1);
                EXPECT_EQ(1u, data.getFieldId());
                EXPECT_TRUE(data.has_ranking_data(3u));
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQ(2u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(2, itr.getElementWeight());
                itr.next();
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(1, itr.getElementWeight());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(search->seek(4));
        EXPECT_TRUE(search->seek(5));
        EXPECT_TRUE(search->seek(6));
        EXPECT_TRUE(search->seek(7));
        EXPECT_TRUE(!search->seek(8));
        EXPECT_TRUE(search->seek(9));
        { // test doc 9 results
            search->unpack(9u);
            {
                TermFieldMatchData &data = *md->resolveTermField(1);
                EXPECT_EQ(1u, data.getFieldId());
                EXPECT_TRUE(data.has_ranking_data(9u));
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQ(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(1, itr.getElementWeight());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(!search->seek(13));
        if (strict) {
            EXPECT_TRUE(search->isAtEnd());
        }
    }
}

TEST_F(FakeSearchableTest, require_that_multi_field_search_works) {
    source.addResult("fieldfoo", "word1",
                     FakeResult().doc(5).pos(3));
    source.addResult("fieldbar", "word1",
                     FakeResult().doc(5).pos(7).doc(10).pos(2));

    SimpleStringTerm termNode("word1", "viewfoobar", 1, w);

    FieldSpecList fields;
    search::fef::MatchDataLayout mdl;
    mdl.allocTermField(0);
    fields.add(FieldSpec("fieldfoo", 1, mdl.allocTermField(1)));
    fields.add(FieldSpec("fieldbar", 2, mdl.allocTermField(2)));
    auto bp = source.createBlueprint(req_ctx, fields, termNode, mdl);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        SCOPED_TRACE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->basic_plan(strict, 100);
        bp->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP search = bp->createSearch(*md);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (strict) {
            EXPECT_EQ(5u, search->getDocId());
        } else {
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
                EXPECT_EQ(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(3u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
            {
                TermFieldMatchData &data = *md->resolveTermField(2);
                EXPECT_EQ(2u, data.getFieldId());
                EXPECT_TRUE(data.has_ranking_data(5u));
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQ(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(7u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(!search->seek(7));
        if (strict) {
            EXPECT_EQ(10u, search->getDocId());
        } else {
            EXPECT_TRUE(search->seek(10u));
        }
        EXPECT_EQ(10u, search->getDocId());
        { // test doc 10 results
            search->unpack(10u);
            {
                TermFieldMatchData &data = *md->resolveTermField(1);
                EXPECT_EQ(1u, data.getFieldId());
                EXPECT_FALSE(data.has_data(10u));
            }
            {
                TermFieldMatchData &data = *md->resolveTermField(2);
                EXPECT_EQ(2u, data.getFieldId());
                EXPECT_TRUE(data.has_ranking_data(10u));
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQ(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQ(2u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(!search->seek(13));
        if (strict) {
            EXPECT_TRUE(search->isAtEnd());
        }
    }
}

TEST_F(FakeSearchableTest, require_that_phrase_with_empty_child_works) {
    source.addResult("fieldfoo", "word1",
                     FakeResult().doc(3).pos(7).doc(5).pos(3));

    SimplePhrase phraseNode("viewfoo", 1, w);
    phraseNode.append(Node::UP(new SimpleStringTerm("word1", "viewfoo", 2, w)));
    phraseNode.append(Node::UP(new SimpleStringTerm("word2", "viewfoo", 3, w)));

    FieldSpecList fields;
    search::fef::MatchDataLayout mdl;
    fields.add(FieldSpec("fieldfoo", 1, mdl.allocTermField(1)));
    auto bp = source.createBlueprint(req_ctx, fields, phraseNode, mdl);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        SCOPED_TRACE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->basic_plan(strict, 100);
        bp->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP search = bp->createSearch(*md);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (strict) {
            EXPECT_TRUE(search->isAtEnd());
        }
    }
}

TEST_F(FakeSearchableTest, require_that_match_data_is_compressed_for_attributes) {
    source.is_attr(true);
    source.addResult("attrfoo", "word1",
                     FakeResult().doc(5).elem(2).weight(6).pos(3).elem(4).weight(8).pos(5));
    SimpleStringTerm termNode("word1", "viewfoo", 1, w);
    FieldSpecList fields;
    search::fef::MatchDataLayout mdl;
    mdl.allocTermField(0);
    fields.add(FieldSpec("attrfoo", 1, mdl.allocTermField(1)));
    auto bp = source.createBlueprint(req_ctx, fields, termNode, mdl);
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    bp->basic_plan(false, 100);
    bp->fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = bp->createSearch(*md);
    search->initFullRange();
    EXPECT_TRUE(search->seek(5));
    search->unpack(5u);
    {
        TermFieldMatchData &data = *md->resolveTermField(1);
        EXPECT_EQ(1u, data.getFieldId());
        EXPECT_TRUE(data.has_ranking_data(5u));
        FieldPositionsIterator itr = data.getIterator();
        EXPECT_EQ(1u, itr.size());
        ASSERT_TRUE(itr.valid());
        EXPECT_EQ(14u, itr.getElementWeight()); // 6 + 8
        itr.next();
        EXPECT_TRUE(!itr.valid());
    }
}

TEST_F(FakeSearchableTest, require_that_relevant_data_can_be_obtained_from_fake_attribute_search_context) {
    source.is_attr(true);
    source.addResult("attrfoo", "word1",
                     FakeResult().doc(5).elem(2).weight(6).pos(3).elem(4).weight(8).pos(5));
    SimpleStringTerm termNode("word1", "viewfoo", 1, w);
    FieldSpecList fields;
    search::fef::MatchDataLayout mdl;
    fields.add(FieldSpec("attrfoo", 1, mdl.allocTermField(1)));
    auto bp = source.createBlueprint(req_ctx, fields, termNode, mdl);
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    bp->basic_plan(false, 100);
    bp->fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = bp->createSearch(*md);
    EXPECT_TRUE(bp->get_attribute_search_context() != nullptr);
    const auto *attr_ctx = bp->get_attribute_search_context();
    ASSERT_TRUE(attr_ctx);
    EXPECT_EQ(attr_ctx->attributeName(), "attrfoo");
    int32_t elem_weight = 0;
    EXPECT_EQ(attr_ctx->find(4, 0, elem_weight), -1);
    int32_t elem_id = attr_ctx->find(5, 0, elem_weight);
    EXPECT_EQ(elem_id, 2);
    EXPECT_EQ(elem_weight, 6);
    elem_id = attr_ctx->find(5, 3, elem_weight);
    EXPECT_EQ(elem_id, 4);
    EXPECT_EQ(elem_weight, 8);
    EXPECT_EQ(attr_ctx->find(5, 5, elem_weight), -1);
    EXPECT_EQ(attr_ctx->find(6, 0, elem_weight), -1);
}

TEST_F(FakeSearchableTest, require_that_repeated_unpack_for_same_docid_is_ignored)
{
    constexpr uint32_t docid = 5;
    constexpr uint32_t another_docid = 10;
    constexpr uint32_t field_id = 1;
    constexpr TermFieldHandle handle = 1;
    constexpr uint32_t docid_limit = 100;
    source.addResult("fieldfoo", "word1", FakeResult().doc(docid).elem(2).pos(3).elem(4).pos(5));
    SimpleStringTerm termNode("word1", "viewfoo", 1, w);
    FieldSpecList fields;
    fields.add(FieldSpec("fieldfoo", field_id, handle));
    search::fef::MatchDataLayout mdl;
    auto bp = source.createBlueprint(req_ctx, fields, termNode, mdl);
    EXPECT_TRUE(mdl.empty());
    MatchData::UP md = MatchData::makeTestInstance(100, 10);
    bp->basic_plan(true, docid_limit);
    bp->fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = bp->createSearch(*md);
    search->initFullRange();

    EXPECT_TRUE(search->seek(docid));
    auto& tfmd = *md->resolveTermField(handle);
    EXPECT_TRUE(tfmd.has_invalid_docid());
    EXPECT_EQ(0u, tfmd.size());
    search->unpack(docid);
    EXPECT_TRUE(tfmd.has_ranking_data(docid));
    EXPECT_EQ(2u, tfmd.size());
    tfmd.reset(another_docid);
    search->unpack(docid);
    EXPECT_TRUE(tfmd.has_ranking_data(another_docid));
}

GTEST_MAIN_RUN_ALL_TESTS()
