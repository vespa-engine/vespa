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

namespace {

class MockHitObserver : public SparseTensorWandQueryNode::HitObserver {
public:
    std::vector<double> hits;
    void on_hit(double score) override { hits.push_back(score); }
};

} // namespace

class SparseTensorWandQueryNodeTest : public testing::Test {
protected:
    QueryTermDataFactory   _factory;
    std::unique_ptr<Query> _query;

    SparseTensorWandQueryNodeTest() : testing::Test(), _factory(nullptr, nullptr), _query() {}
    ~SparseTensorWandQueryNodeTest() override;

    SparseTensorWandQueryNode& build_query() {
        QueryBuilder<SimpleQueryNodeTypes> builder;
        builder.add_sparse_tensor_wand_term("qtensor", "field", 42, Weight(1), 100, 2.5);
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
    EXPECT_EQ(100u, node.get_target_hits());
    EXPECT_EQ(2.5, node.get_score_threshold());
    EXPECT_TRUE(node.is_sparse_tensor_wand());
    EXPECT_FALSE(node.evaluate());
}

TEST_F(SparseTensorWandQueryNodeTest, unpack_match_data_sets_raw_score_and_notifies_observer) {
    auto&           node = build_query();
    MockHitObserver observer;
    node.set_hit_observer(&observer);
    auto&              td = static_cast<QueryTermData&>(node.getQueryItem()).getTermData();
    MatchDataLayout    mdl;
    constexpr uint32_t field_id = 12;
    TermFieldHandle    handle = mdl.allocTermField(field_id);
    td.addField(field_id).setHandle(handle);
    auto             md = mdl.createMatchData();
    auto             tfmd = md->resolveTermField(handle);
    IndexEnvironment ie;

    node.unpack_match_data(1, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd->has_invalid_docid());
    EXPECT_TRUE(observer.hits.empty());

    node.set_score(7.5);
    EXPECT_TRUE(node.evaluate());
    node.unpack_match_data(2, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd->has_ranking_data(2));
    EXPECT_EQ(7.5, tfmd->getRawScore());
    EXPECT_EQ(std::vector<double>({7.5}), observer.hits);

    node.reset();
    EXPECT_FALSE(node.evaluate());
    node.unpack_match_data(3, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd->has_ranking_data(2));
    EXPECT_EQ(1u, observer.hits.size());
}
