// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/fef/matchdata.h>

#include <vespa/log/log.h>
LOG_SETUP("fake_searchable_test");

using namespace search::queryeval;
using namespace search::query;
using namespace search::fef;

class Test : public vespalib::TestApp {
public:
    ~Test();
    int Main() override;
    void testTestFakeResult();
    void testTerm();
    void testPhrase();
    void testWeightedSet();
    void testMultiField();
    void testPhraseWithEmptyChild();
private:
    FakeRequestContext _requestContext;
};

Test::~Test() {}
void
Test::testTestFakeResult()
{
    EXPECT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).pos(6).elem(6).doc(6),
               FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).pos(6).elem(6).doc(6));

    EXPECT_NOT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5),
                   FakeResult().doc(1).elem(5).len(15).weight(5).pos(5));

    EXPECT_NOT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5),
                   FakeResult().doc(5).elem(1).len(15).weight(5).pos(5));

    EXPECT_NOT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5),
                   FakeResult().doc(5).elem(5).len(19).weight(5).pos(5));

    EXPECT_NOT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5),
                   FakeResult().doc(5).elem(5).len(15).weight(1).pos(5));

    EXPECT_NOT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5),
                   FakeResult().doc(5).elem(5).len(15).weight(5).pos(1));

    EXPECT_NOT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5),
                   FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).doc(6));

    EXPECT_NOT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5),
                   FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).elem(6));

    EXPECT_NOT_EQUAL(FakeResult().doc(5).elem(5).len(15).weight(5).pos(5),
                   FakeResult().doc(5).elem(5).len(15).weight(5).pos(5).pos(6));
}

void
Test::testTerm()
{
    Weight w(100);

    FakeSearchable source;
    source.addResult("fieldfoo", "word1",
                     FakeResult().doc(5).pos(3));

    SimpleStringTerm termNode("word1", "viewfoo", 1, w);

    FieldSpecList fields;
    fields.add(FieldSpec("fieldfoo", 1, 1));
    Blueprint::UP bp = source.createBlueprint(_requestContext, fields, termNode);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        TEST_STATE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->fetchPostings(strict);
        SearchIterator::UP search = bp->createSearch(*md, strict);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (strict) {
            EXPECT_EQUAL(5u, search->getDocId());
        } else {
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
                EXPECT_EQUAL(3u, itr.getPosition());
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

void
Test::testPhrase()
{
    Weight w(100);

    FakeSearchable source;
    source.addResult("fieldfoo", "word1",
                     FakeResult().doc(3).pos(7).doc(5).pos(3));
    source.addResult("fieldfoo", "word2",
                     FakeResult().doc(2).pos(1).doc(3).pos(10).doc(5).pos(4));

    SimplePhrase phraseNode("viewfoo", 1, w);
    phraseNode.append(Node::UP(new SimpleStringTerm("word1", "viewfoo", 2, w)));
    phraseNode.append(Node::UP(new SimpleStringTerm("word2", "viewfoo", 3, w)));

    FieldSpecList fields;
    fields.add(FieldSpec("fieldfoo", 1, 1));
    Blueprint::UP bp = source.createBlueprint(_requestContext, fields, phraseNode);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        TEST_STATE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->fetchPostings(strict);
        SearchIterator::UP search = bp->createSearch(*md, strict);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (strict) {
            EXPECT_EQUAL(5u, search->getDocId());
        } else {
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
                EXPECT_EQUAL(3u, itr.getPosition());
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

void
Test::testWeightedSet()
{
    Weight w(100);

    FakeSearchable source;
    source.addResult("fieldfoo", "friend1",
                     FakeResult().doc(3).doc(5).doc(7).doc(9));
    source.addResult("fieldfoo", "friend2",
                     FakeResult().doc(3).doc(4).doc(5).doc(6));
    source.addResult("fieldfoo", "friend3",
                     FakeResult().doc(5));

    SimpleWeightedSetTerm weightedSet("fieldfoo", 1, w);
    weightedSet.append(Node::UP(new SimpleStringTerm("friend1", "fieldfoo", 2, Weight(1))));
    weightedSet.append(Node::UP(new SimpleStringTerm("friend2", "fieldfoo", 3, Weight(2))));

    FieldSpecList fields;
    fields.add(FieldSpec("fieldfoo", 1, 1));
    Blueprint::UP bp = source.createBlueprint(_requestContext, fields, weightedSet);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        TEST_STATE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->fetchPostings(strict);
        SearchIterator::UP search = bp->createSearch(*md, strict);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(2));
        if (strict) {
            EXPECT_EQUAL(3u, search->getDocId());
        } else {
            EXPECT_TRUE(search->seek(3u));
        }
        EXPECT_EQUAL(3u, search->getDocId());
        { // test doc 3 results
            search->unpack(3u);
            {
                TermFieldMatchData &data = *md->resolveTermField(1);
                EXPECT_EQUAL(1u, data.getFieldId());
                EXPECT_EQUAL(3u, data.getDocId());
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQUAL(2u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(2, itr.getElementWeight());
                itr.next();
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(1, itr.getElementWeight());
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
                EXPECT_EQUAL(1u, data.getFieldId());
                EXPECT_EQUAL(9u, data.getDocId());
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQUAL(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(1, itr.getElementWeight());
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

void
Test::testMultiField()
{
    Weight w(100);

    FakeSearchable source;
    source.addResult("fieldfoo", "word1",
                     FakeResult().doc(5).pos(3));
    source.addResult("fieldbar", "word1",
                     FakeResult().doc(5).pos(7).doc(10).pos(2));

    SimpleStringTerm termNode("word1", "viewfoobar", 1, w);

    FieldSpecList fields;
    fields.add(FieldSpec("fieldfoo", 1, 1));
    fields.add(FieldSpec("fieldbar", 2, 2));
    Blueprint::UP bp = source.createBlueprint(_requestContext, fields, termNode);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        TEST_STATE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->fetchPostings(strict);
        SearchIterator::UP search = bp->createSearch(*md, strict);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (strict) {
            EXPECT_EQUAL(5u, search->getDocId());
        } else {
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
                EXPECT_EQUAL(3u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
            {
                TermFieldMatchData &data = *md->resolveTermField(2);
                EXPECT_EQUAL(2u, data.getFieldId());
                EXPECT_EQUAL(5u, data.getDocId());
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQUAL(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(7u, itr.getPosition());
                itr.next();
                EXPECT_TRUE(!itr.valid());
            }
        }
        EXPECT_TRUE(!search->seek(7));
        if (strict) {
            EXPECT_EQUAL(10u, search->getDocId());
        } else {
            EXPECT_TRUE(search->seek(10u));
        }
        EXPECT_EQUAL(10u, search->getDocId());
        { // test doc 10 results
            search->unpack(10u);
            {
                TermFieldMatchData &data = *md->resolveTermField(1);
                EXPECT_EQUAL(1u, data.getFieldId());
                EXPECT_NOT_EQUAL(10u, data.getDocId());
            }
            {
                TermFieldMatchData &data = *md->resolveTermField(2);
                EXPECT_EQUAL(2u, data.getFieldId());
                EXPECT_EQUAL(10u, data.getDocId());
                FieldPositionsIterator itr = data.getIterator();
                EXPECT_EQUAL(1u, itr.size());
                ASSERT_TRUE(itr.valid());
                EXPECT_EQUAL(2u, itr.getPosition());
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

void
Test::testPhraseWithEmptyChild()
{
    Weight w(100);

    FakeSearchable source;
    source.addResult("fieldfoo", "word1",
                     FakeResult().doc(3).pos(7).doc(5).pos(3));

    SimplePhrase phraseNode("viewfoo", 1, w);
    phraseNode.append(Node::UP(new SimpleStringTerm("word1", "viewfoo", 2, w)));
    phraseNode.append(Node::UP(new SimpleStringTerm("word2", "viewfoo", 3, w)));

    FieldSpecList fields;
    fields.add(FieldSpec("fieldfoo", 1, 1));
    Blueprint::UP bp = source.createBlueprint(_requestContext, fields, phraseNode);
    for (int i = 0; i <= 1; ++i) {
        bool strict = (i == 0);
        TEST_STATE(strict ? "strict" : "non-strict");
        MatchData::UP md = MatchData::makeTestInstance(100, 10);
        bp->fetchPostings(strict);
        SearchIterator::UP search = bp->createSearch(*md, strict);
        search->initFullRange();

        EXPECT_TRUE(!search->seek(3));
        if (strict) {
            EXPECT_TRUE(search->isAtEnd());
        }
    }
}

int
Test::Main()
{
    TEST_INIT("fake_searchable_test");
    testTestFakeResult();
    testTerm();
    testPhrase();
    testWeightedSet();
    testMultiField();
    testPhraseWithEmptyChild();
    TEST_DONE();
}

TEST_APPHOOK(Test);
