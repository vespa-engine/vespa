// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchvisitor/querywrapper.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>

using namespace search;
using namespace search::query;
using namespace search::streaming;

namespace streaming {

TEST(QueryWrapperTest, test_query_wrapper)
{
    QueryNodeResultFactory empty;
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
        q.getLeaves(terms);
        ASSERT_TRUE(tl.size() == 3 && terms.size() == 3);
        for (size_t i = 0; i < 3; ++i) {
            EXPECT_EQ(tl[i], terms[i]);
            std::cout << "t[" << i << "]:" << terms[i] << std::endl;
            auto phrase = dynamic_cast<PhraseQueryNode*>(terms[i]);
            EXPECT_EQ(i == 1, phrase != nullptr);
            if (i == 1) {
                EXPECT_EQ(3u, phrase->get_terms().size());
            }
        }
    }
}

}

GTEST_MAIN_RUN_ALL_TESTS()
