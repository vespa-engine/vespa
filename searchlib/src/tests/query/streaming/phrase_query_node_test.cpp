// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/streaming/phrase_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::query::QueryBuilder;
using search::query::Node;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::streaming::HitList;
using search::streaming::PhraseQueryNode;
using search::streaming::Query;
using search::streaming::QueryTerm;
using search::streaming::QueryNodeRefList;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTermList;

TEST(PhraseQueryNodeTest, test_phrase_evaluate)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addPhrase(3, "", 0, Weight(0));
    {
        builder.addStringTerm("a", "", 0, Weight(0));
        builder.addStringTerm("b", "", 0, Weight(0));
        builder.addStringTerm("c", "", 0, Weight(0));
    }
    Node::UP node = builder.build();
    vespalib::string stackDump = StackDumpCreator::create(*node);
    QueryNodeResultFactory empty;
    Query q(empty, stackDump);
    auto& p = dynamic_cast<PhraseQueryNode&>(q.getRoot());
    auto& terms = p.get_terms();
    for (auto& qt : terms) {
        qt->resizeFieldId(1);
    }

    // field 0
    terms[0]->add(0, 0, 1, 0);
    terms[1]->add(0, 0, 1, 1);
    terms[2]->add(0, 0, 1, 2);
    terms[0]->add(0, 0, 1, 7);
    terms[1]->add(0, 1, 1, 8);
    terms[2]->add(0, 0, 1, 9);
    // field 1
    terms[0]->add(1, 0, 1, 4);
    terms[1]->add(1, 0, 1, 5);
    terms[2]->add(1, 0, 1, 6);
    // field 2 (not complete match)
    terms[0]->add(2, 0, 1, 1);
    terms[1]->add(2, 0, 1, 2);
    terms[2]->add(2, 0, 1, 4);
    // field 3
    terms[0]->add(3, 0, 1, 0);
    terms[1]->add(3, 0, 1, 1);
    terms[2]->add(3, 0, 1, 2);
    // field 4 (not complete match)
    terms[0]->add(4, 0, 1, 1);
    terms[1]->add(4, 0, 1, 2);
    // field 5 (not complete match)
    terms[0]->add(5, 0, 1, 2);
    terms[1]->add(5, 0, 1, 1);
    terms[2]->add(5, 0, 1, 0);
    HitList hits;
    p.evaluateHits(hits);
    ASSERT_EQ(3u, hits.size());
    EXPECT_EQ(0u, hits[0].field_id());
    EXPECT_EQ(0u, hits[0].element_id());
    EXPECT_EQ(0u, hits[0].position());
    EXPECT_EQ(1u, hits[1].field_id());
    EXPECT_EQ(0u, hits[1].element_id());
    EXPECT_EQ(4u, hits[1].position());
    EXPECT_EQ(3u, hits[2].field_id());
    EXPECT_EQ(0u, hits[2].element_id());
    EXPECT_EQ(0u, hits[2].position());
    EXPECT_TRUE(p.evaluate());
}

GTEST_MAIN_RUN_ALL_TESTS()
