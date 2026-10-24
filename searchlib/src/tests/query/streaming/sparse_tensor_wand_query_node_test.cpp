// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/query_term_data.h>
#include <vespa/searchlib/query/streaming/sparse_tensor_wand_query_node.h>
#include <vespa/searchlib/query/tree/query_to_protobuf.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::SerializedQueryTree;
using search::common::ElementIds;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::test::IndexEnvironment;
using search::query::QueryBuilder;
using search::query::QueryToProtobuf;
using search::query::SimpleQueryNodeTypes;
using search::query::Weight;
using search::streaming::Query;
using search::streaming::QueryTermData;
using search::streaming::QueryTermDataFactory;
using search::streaming::QueryTermList;
using search::streaming::SparseTensorWandQueryNode;

class SparseTensorWandQueryNodeTest : public testing::Test {
protected:
    QueryTermDataFactory   _factory;
    std::unique_ptr<Query> _query;

    SparseTensorWandQueryNodeTest() : testing::Test(), _factory(nullptr, nullptr), _query() {}
    ~SparseTensorWandQueryNodeTest() override;

    SparseTensorWandQueryNode& build_query(uint32_t target_hits = 100) {
        QueryBuilder<SimpleQueryNodeTypes> builder;
        builder.add_sparse_tensor_wand_term("qtensor", "field", 42, Weight(17), target_hits, 2.5);
        auto            node = builder.build();
        QueryToProtobuf converter;
        auto            proto_query_tree = converter.serialize(*node);
        auto            serialized_query_tree =
            SerializedQueryTree::fromProtobuf(std::make_unique<decltype(proto_query_tree)>(proto_query_tree));
        _query = std::make_unique<Query>(_factory, *serialized_query_tree);
        QueryTermList term_list;
        _query->getLeaves(term_list);
        EXPECT_EQ(1u, term_list.size());
        auto* result = term_list.front()->as_sparse_tensor_wand_query_node();
        EXPECT_NE(nullptr, result);
        return *result;
    }
};

SparseTensorWandQueryNodeTest::~SparseTensorWandQueryNodeTest() = default;

TEST_F(SparseTensorWandQueryNodeTest, query_node_is_built_from_protobuf_query_tree) {
    auto& node = build_query();
    EXPECT_EQ("qtensor", node.get_query_tensor_name());
    EXPECT_EQ("field", node.index());
    EXPECT_EQ(42u, node.uniqueId());
    EXPECT_EQ(17, node.weight().percent());
    EXPECT_EQ(100u, node.get_target_hits());
    EXPECT_EQ(2.5, node.get_score_threshold());
    EXPECT_TRUE(node.is_sparse_tensor_wand());
    EXPECT_FALSE(node.evaluate());
}

TEST_F(SparseTensorWandQueryNodeTest, unpack_match_data_sets_raw_score_per_field) {
    auto&              node = build_query();
    auto&              td = static_cast<QueryTermData&>(node.getQueryItem()).getTermData();
    MatchDataLayout    mdl;
    constexpr uint32_t field_a = 12;
    constexpr uint32_t field_b = 13;
    TermFieldHandle    handle_a = mdl.allocTermField(field_a);
    TermFieldHandle    handle_b = mdl.allocTermField(field_b);
    td.addField(field_a).setHandle(handle_a);
    td.addField(field_b).setHandle(handle_b);
    auto             md = mdl.createMatchData();
    auto             tfmd_a = md->resolveTermField(handle_a);
    auto             tfmd_b = md->resolveTermField(handle_b);
    IndexEnvironment ie;

    node.unpack_match_data(1, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd_a->has_invalid_docid());
    EXPECT_TRUE(tfmd_b->has_invalid_docid());

    node.add_field_score(field_a, 7.5);
    EXPECT_TRUE(node.evaluate());
    node.unpack_match_data(2, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd_a->has_ranking_data(2));
    EXPECT_EQ(7.5, tfmd_a->getRawScore());
    EXPECT_TRUE(tfmd_b->has_invalid_docid());

    node.reset();
    node.add_field_score(field_a, 3.0);
    node.add_field_score(field_b, 4.0);
    node.unpack_match_data(3, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd_a->has_ranking_data(3));
    EXPECT_EQ(3.0, tfmd_a->getRawScore());
    EXPECT_TRUE(tfmd_b->has_ranking_data(3));
    EXPECT_EQ(4.0, tfmd_b->getRawScore());

    node.reset();
    EXPECT_FALSE(node.evaluate());
    node.unpack_match_data(4, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd_a->has_ranking_data(3));
    EXPECT_TRUE(tfmd_b->has_ranking_data(3));
}

TEST_F(SparseTensorWandQueryNodeTest, best_field_score_of_each_hit_raises_score_threshold_once) {
    auto&            node = build_query(2);
    auto&            td = static_cast<QueryTermData&>(node.getQueryItem()).getTermData();
    MatchDataLayout  mdl;
    td.addField(12).setHandle(mdl.allocTermField(12));
    auto             md = mdl.createMatchData();
    IndexEnvironment ie;
    EXPECT_EQ(2.5, node.get_min_score());

    node.add_field_score(12, 4.0);
    node.add_field_score(13, 6.0);
    // Unpacking the same document more than once (e.g. when dumping rank features) adds it to the heap once
    node.unpack_match_data(1, *md, ie, ElementIds::select_all());
    node.unpack_match_data(1, *md, ie, ElementIds::select_all());
    EXPECT_EQ(2.5, node.get_min_score());

    node.reset();
    node.add_field_score(12, 5.0);
    node.unpack_match_data(2, *md, ie, ElementIds::select_all());
    // The heap now has 5 and 6
    EXPECT_EQ(5.0, node.get_min_score());
}
