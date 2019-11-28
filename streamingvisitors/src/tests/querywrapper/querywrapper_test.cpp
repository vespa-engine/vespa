// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchvisitor/querywrapper.h>
#include <iostream>

using namespace search;
using namespace search::query;
using namespace search::streaming;

namespace storage {

class QueryWrapperTest : public vespalib::TestApp
{
private:
    void testQueryWrapper();

public:
    int Main() override;
};

void
QueryWrapperTest::testQueryWrapper()
{
    QueryNodeResultFactory empty;
    PhraseQueryNode * null = NULL;
    {
        QueryBuilder<SimpleQueryNodeTypes> builder;
        builder.addAnd(3);
        {
            builder.addStringTerm("a", "", 0, Weight(0));
            builder.addPhrase(3, "", 0, Weight(0));
            {
                builder.addStringTerm("b", "", 0, Weight(0));
                builder.addStringTerm("c", "", 0, Weight(0));
                builder.addStringTerm("d", "", 0, Weight(0));
            }
            builder.addStringTerm("e", "", 0, Weight(0));
        }
        Node::UP node = builder.build();
        vespalib::string stackDump = StackDumpCreator::create(*node);
        Query q(empty, stackDump);
        QueryWrapper wrap(q);
        QueryWrapper::TermList & tl = wrap.getTermList();

        QueryTermList terms;
        q.getLeafs(terms);
        ASSERT_TRUE(tl.size() == 5 && terms.size() == 5);
        for (size_t i = 0; i < 5; ++i) {
            EXPECT_EQUAL(tl[i].getTerm(), terms[i]);
            std::cout << "t[" << i << "]:" << terms[i] << std::endl;
        }

        QueryNodeRefList phrases;
        q.getPhrases(phrases);
        for (size_t i = 0; i < phrases.size(); ++i) {
            std::cout << "p[" << i << "]:" << phrases[i] << std::endl;
        }
        EXPECT_EQUAL(phrases.size(), 1u);
        ASSERT_TRUE(phrases.size() == 1);
        EXPECT_EQUAL(tl[0].getParent(), null);
        EXPECT_EQUAL(tl[1].getParent(), phrases[0]);
        EXPECT_EQUAL(tl[2].getParent(), phrases[0]);
        EXPECT_EQUAL(tl[3].getParent(), phrases[0]);
        EXPECT_EQUAL(tl[4].getParent(), null);

        EXPECT_EQUAL(tl[0].getIndex(), 0u);
        EXPECT_EQUAL(tl[1].getIndex(), 0u);
        EXPECT_EQUAL(tl[2].getIndex(), 1u);
        EXPECT_EQUAL(tl[3].getIndex(), 2u);
        EXPECT_EQUAL(tl[4].getIndex(), 0u);

        EXPECT_TRUE(!tl[0].isFirstPhraseTerm());
        EXPECT_TRUE( tl[1].isFirstPhraseTerm());
        EXPECT_TRUE(!tl[2].isFirstPhraseTerm());
        EXPECT_TRUE(!tl[3].isFirstPhraseTerm());
        EXPECT_TRUE(!tl[4].isFirstPhraseTerm());

        EXPECT_TRUE(!tl[0].isPhraseTerm());
        EXPECT_TRUE( tl[1].isPhraseTerm());
        EXPECT_TRUE( tl[2].isPhraseTerm());
        EXPECT_TRUE( tl[3].isPhraseTerm());
        EXPECT_TRUE(!tl[4].isPhraseTerm());

        EXPECT_EQUAL(tl[0].getPosAdjust(), 0u);
        EXPECT_EQUAL(tl[1].getPosAdjust(), 2u);
        EXPECT_EQUAL(tl[2].getPosAdjust(), 2u);
        EXPECT_EQUAL(tl[3].getPosAdjust(), 2u);
        EXPECT_EQUAL(tl[4].getPosAdjust(), 0u);
    }
}

int
QueryWrapperTest::Main()
{
    TEST_INIT("querywrapper_test");

    testQueryWrapper();

    TEST_DONE();
}

} // namespace storage

TEST_APPHOOK(storage::QueryWrapperTest)
