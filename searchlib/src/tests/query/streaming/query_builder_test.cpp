// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/query/streaming/near_query_node.h>
#include <vespa/searchlib/query/streaming/onear_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/query/tree/query_to_protobuf.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::SerializedQueryTree;
using search::query::QueryBuilder;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::streaming::Query;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTermList;

TEST(StreamingQueryBuilderTest, hidden_terms_are_not_ranked)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addAnd(2);
    builder.addAndNot(2);
    builder.addStringTerm("a", "", 0, Weight(0));
    builder.addStringTerm("b", "", 0, Weight(0));
    builder.addStringTerm("c", "", 0, Weight(0));
    auto node =  builder.build();
    std::string stackDump = StackDumpCreator::create(*node);
    auto serializedQueryTree = SerializedQueryTree::fromStackDump(stackDump);
    QueryNodeResultFactory empty;
    Query q(empty, *serializedQueryTree);
    QueryTermList terms;
    q.getRoot().getLeaves(terms);
    EXPECT_EQ(3, terms.size());
    EXPECT_TRUE(terms[0]->isRanked());
    EXPECT_FALSE(terms[1]->isRanked());
    EXPECT_TRUE(terms[2]->isRanked());
}

TEST(StreamingQueryBuilderTest, near_with_negative_terms)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addNear(3, 5, 1, 3);  // 3 children, distance=5, 1 negative term, exclusion_distance=3
    builder.addStringTerm("a", "", 0, Weight(0));
    builder.addStringTerm("b", "", 0, Weight(0));
    builder.addStringTerm("x", "", 0, Weight(0));  // negative term
    auto node = builder.build();

    // Convert to protobuf (stack dump doesn't support negative terms)
    search::query::QueryToProtobuf converter;
    auto protoQueryTree = converter.serialize(*node);

    // Build streaming query from protobuf
    auto serializedQueryTree = SerializedQueryTree::fromProtobuf(
        std::make_unique<decltype(protoQueryTree)>(protoQueryTree));
    QueryNodeResultFactory empty;
    Query q(empty, *serializedQueryTree);

    // Verify the Near node parameters
    auto& near = dynamic_cast<search::streaming::NearQueryNode&>(q.getRoot());
    EXPECT_EQ(5, near.distance());
    EXPECT_EQ(1, near.num_negative_terms());
    EXPECT_EQ(3, near.exclusion_distance());

    // Verify term ranking status
    QueryTermList terms;
    q.getRoot().getLeaves(terms);
    EXPECT_EQ(3, terms.size());
    EXPECT_TRUE(terms[0]->isRanked());   // "a" is ranked
    EXPECT_TRUE(terms[1]->isRanked());   // "b" is ranked
    EXPECT_FALSE(terms[2]->isRanked());  // "x" is NOT ranked (negative term)
}

TEST(StreamingQueryBuilderTest, onear_with_negative_terms)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addONear(3, 5, 2, 3);  // 3 children, distance=5, 2 negative terms, exclusion_distance=3
    builder.addStringTerm("a", "", 0, Weight(0));
    builder.addStringTerm("x", "", 0, Weight(0));  // negative term
    builder.addStringTerm("y", "", 0, Weight(0));  // negative term
    auto node = builder.build();

    // Convert to protobuf (stack dump doesn't support negative terms)
    search::query::QueryToProtobuf converter;
    auto protoQueryTree = converter.serialize(*node);

    // Build streaming query from protobuf
    auto serializedQueryTree = SerializedQueryTree::fromProtobuf(
        std::make_unique<decltype(protoQueryTree)>(protoQueryTree));
    QueryNodeResultFactory empty;
    Query q(empty, *serializedQueryTree);

    // Verify the ONear node parameters
    auto& onear = dynamic_cast<search::streaming::ONearQueryNode&>(q.getRoot());
    EXPECT_EQ(5, onear.distance());
    EXPECT_EQ(2, onear.num_negative_terms());
    EXPECT_EQ(3, onear.exclusion_distance());

    // Verify term ranking status
    QueryTermList terms;
    q.getRoot().getLeaves(terms);
    EXPECT_EQ(3, terms.size());
    EXPECT_TRUE(terms[0]->isRanked());   // "a" is ranked
    EXPECT_FALSE(terms[1]->isRanked());  // "x" is NOT ranked (negative term)
    EXPECT_FALSE(terms[2]->isRanked());  // "y" is NOT ranked (negative term)
}
