// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::query::QueryBuilder;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::streaming::AndNotQueryNode;
using search::streaming::Query;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTermList;

TEST(StreamingQueryBuilderTest, hidden_terms_are_not_ranked)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAndNot(2);
    {
        builder.addStringTerm("a", "", 0, Weight(0));
        builder.addStringTerm("b", "", 0, Weight(0));
    }
    auto node =  builder.build();
    std::string stackDump = StackDumpCreator::create(*node);
    QueryNodeResultFactory empty;
    Query q(empty, stackDump);
    auto* p = dynamic_cast<AndNotQueryNode*>(&q.getRoot());
    ASSERT_NE(nullptr, p);
    QueryTermList terms;
    p->getLeaves(terms);
    EXPECT_EQ(2, terms.size());
    EXPECT_TRUE(terms[0]->isRanked());
    EXPECT_FALSE(terms[1]->isRanked());
}

GTEST_MAIN_RUN_ALL_TESTS()
