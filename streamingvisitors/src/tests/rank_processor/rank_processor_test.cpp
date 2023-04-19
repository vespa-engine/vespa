// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchvisitor/rankprocessor.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/nearest_neighbor_query_node.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchvisitor/querytermdata.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::fef::MatchData;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::query::Weight;
using search::query::QueryBuilder;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::streaming::NearestNeighborQueryNode;
using search::streaming::Query;
using streaming::RankProcessor;
using streaming::QueryTermData;
using streaming::QueryTermDataFactory;
using streaming::QueryWrapper;

class RankProcessorTest : public testing::Test
{
protected:
    QueryTermDataFactory _factory;
    std::unique_ptr<Query> _query;
    std::unique_ptr<QueryWrapper> _query_wrapper;

    RankProcessorTest();
    ~RankProcessorTest() override;

    void build_query(QueryBuilder<SimpleQueryNodeTypes> &builder);
};

RankProcessorTest::RankProcessorTest()
    : testing::Test(),
      _factory(),
      _query(),
      _query_wrapper()
{
}

RankProcessorTest::~RankProcessorTest() = default;

void
RankProcessorTest::build_query(QueryBuilder<SimpleQueryNodeTypes> &builder)
{
    auto build_node = builder.build();
    auto stack_dump = StackDumpCreator::create(*build_node);
    _query = std::make_unique<Query>(_factory, stack_dump);
    _query_wrapper = std::make_unique<QueryWrapper>(*_query);
}


TEST_F(RankProcessorTest, unpack_match_data_for_nearest_neighbor_query_node)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    constexpr double distance_threshold = 35.5;
    constexpr int32_t id = 42;
    constexpr int32_t weight = 1;
    constexpr uint32_t target_num_hits = 100;
    constexpr bool allow_approximate = false;
    constexpr uint32_t explore_additional_hits = 800;
    builder.add_nearest_neighbor_term("qtensor", "field", id, Weight(weight), target_num_hits, allow_approximate, explore_additional_hits, distance_threshold);
    build_query(builder);
    auto& term_list = _query_wrapper->getTermList();
    EXPECT_EQ(1u, term_list.size());
    auto node = dynamic_cast<NearestNeighborQueryNode*>(term_list.front().getTerm());
    EXPECT_NE(nullptr, node);
    auto& qtd = static_cast<QueryTermData &>(node->getQueryItem());
    auto& td = qtd.getTermData();
    constexpr TermFieldHandle handle = 27;
    constexpr uint32_t field_id = 12;
    td.addField(field_id).setHandle(handle);
    auto md = MatchData::makeTestInstance(handle + 1, handle + 1);
    auto tfmd = md->resolveTermField(handle);
    auto invalid_id = TermFieldMatchData::invalidId();
    EXPECT_EQ(invalid_id, tfmd->getDocId());
    RankProcessor::unpack_match_data(1, *md, *_query_wrapper);
    EXPECT_EQ(invalid_id, tfmd->getDocId());
    constexpr double raw_score = 1.5;
    node->set_raw_score(raw_score);
    RankProcessor::unpack_match_data(2, *md, *_query_wrapper);
    EXPECT_EQ(2, tfmd->getDocId());
    EXPECT_EQ(raw_score, tfmd->getRawScore());
    node->reset();
    RankProcessor::unpack_match_data(3, *md, *_query_wrapper);
    EXPECT_EQ(2, tfmd->getDocId());
}

GTEST_MAIN_RUN_ALL_TESTS()
