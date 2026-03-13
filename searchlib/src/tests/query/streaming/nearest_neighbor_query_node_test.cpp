// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/streaming/nearest_neighbor_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/query_term_data.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::common::ElementIds;
using search::fef::MatchData;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::fef::test::IndexEnvironment;
using search::query::Weight;
using search::query::QueryBuilder;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::streaming::NearestNeighborQueryNode;
using search::streaming::Query;
using search::streaming::QueryTerm;
using search::streaming::QueryTermData;
using search::streaming::QueryTermDataFactory;
using search::streaming::QueryTermList;

class NearestNeighborQueryNodeTest : public testing::Test
{
protected:
    QueryTermDataFactory _factory;
    std::unique_ptr<Query> _query;

    NearestNeighborQueryNodeTest();
    ~NearestNeighborQueryNodeTest() override;

    void build_query(QueryBuilder<SimpleQueryNodeTypes> &builder);
};

NearestNeighborQueryNodeTest::NearestNeighborQueryNodeTest()
    : testing::Test(),
      _factory(nullptr, nullptr),
      _query()
{
}

NearestNeighborQueryNodeTest::~NearestNeighborQueryNodeTest() = default;

void
NearestNeighborQueryNodeTest::build_query(QueryBuilder<SimpleQueryNodeTypes> &builder)
{
    auto build_node = builder.build();
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*build_node);
    _query = std::make_unique<Query>(_factory, *serializedQueryTree);
}

namespace {

class MockRawScoreCalculator : public search::streaming::NearestNeighborQueryNode::RawScoreCalculator {
public:
    double to_raw_score(double distance) override { return distance * 2; }
};

}

TEST_F(NearestNeighborQueryNodeTest, unpack_match_data_for_nearest_neighbor_query_node)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    constexpr double distance_threshold = 35.5;
    constexpr int32_t id = 42;
    constexpr int32_t weight = 1;
    constexpr uint32_t target_num_hits = 100;
    constexpr bool allow_approximate = false;
    constexpr uint32_t explore_additional_hits = 800;
    search::query::NearestNeighborTerm::HnswParams hnsw_params;
    hnsw_params.distance_threshold = distance_threshold;
    hnsw_params.explore_additional_hits = explore_additional_hits;
    builder.add_nearest_neighbor_term("qtensor", "field", id, Weight(weight), target_num_hits, allow_approximate, hnsw_params);
    build_query(builder);
    QueryTermList term_list;
    _query->getLeaves(term_list);
    ASSERT_EQ(1u, term_list.size());
    auto node = dynamic_cast<NearestNeighborQueryNode*>(term_list.front());
    EXPECT_NE(nullptr, node);
    MockRawScoreCalculator calc;
    node->set_raw_score_calc(&calc);
    auto& qtd = static_cast<QueryTermData &>(node->getQueryItem());
    auto& td = qtd.getTermData();
    constexpr TermFieldHandle handle = 27;
    constexpr uint32_t field_id = 12;
    td.addField(field_id).setHandle(handle);
    auto md = MatchData::makeTestInstance(handle + 1, handle + 1);
    auto tfmd = md->resolveTermField(handle);
    EXPECT_TRUE(tfmd->has_invalid_docid());
    IndexEnvironment ie;
    node->unpack_match_data(1, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd->has_invalid_docid());
    constexpr double distance = 1.5;
    node->set_distance(distance);
    node->unpack_match_data(2, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd->has_ranking_data(2));
    EXPECT_EQ(distance * 2, tfmd->getRawScore());
    node->reset();
    node->unpack_match_data(3, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd->has_ranking_data(2));
}
