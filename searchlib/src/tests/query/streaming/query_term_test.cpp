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
using search::streaming::Query;
using search::streaming::QueryTerm;
using search::streaming::QueryTermData;
using search::streaming::QueryTermDataFactory;
using search::streaming::QueryTermList;

class QueryTermTest : public testing::Test
{
protected:
    QueryTermDataFactory _factory;
    std::unique_ptr<Query> _query;

    QueryTermTest();
    ~QueryTermTest() override;

    void build_query(QueryBuilder<SimpleQueryNodeTypes>& builder);
    void test_unpack_match_data_for_term_node(bool interleaved_features);
};

QueryTermTest::QueryTermTest()
    : testing::Test(),
      _factory(nullptr, nullptr),
      _query()
{
}

QueryTermTest::~QueryTermTest() = default;

void
QueryTermTest::build_query(QueryBuilder<SimpleQueryNodeTypes> &builder)
{
    auto build_node = builder.build();
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*build_node);
    _query = std::make_unique<Query>(_factory, *serializedQueryTree);
}

void
QueryTermTest::test_unpack_match_data_for_term_node(bool interleaved_features)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    constexpr int32_t id = 42;
    constexpr int32_t weight = 1;
    builder.addStringTerm("term", "field", id, Weight(weight));
    build_query(builder);
    QueryTermList term_list;
    _query->getLeaves(term_list);
    ASSERT_EQ(1u, term_list.size());
    auto* node = dynamic_cast<QueryTerm*>(term_list.front());
    EXPECT_NE(nullptr, node);
    auto& qtd = static_cast<QueryTermData &>(node->getQueryItem());
    auto& td = qtd.getTermData();
    constexpr TermFieldHandle handle = 27;
    constexpr uint32_t field_id = 12;
    constexpr uint32_t mock_num_occs = 2;
    constexpr uint32_t mock_field_length = 101;
    td.addField(field_id).setHandle(handle);
    node->resizeFieldId(field_id);
    auto md = MatchData::makeTestInstance(handle + 1, handle + 1);
    auto tfmd = md->resolveTermField(handle);
    tfmd->setNeedInterleavedFeatures(interleaved_features);
    auto invalid_id = TermFieldMatchData::invalidId();
    EXPECT_EQ(invalid_id, tfmd->getDocId());
    IndexEnvironment ie;
    node->unpack_match_data(1, *md, ie, ElementIds::select_all());
    EXPECT_EQ(invalid_id, tfmd->getDocId());
    node->add(field_id, 0, 1, 0);
    node->add(field_id, 0, 1, 1);
    auto& field_info = node->getFieldInfo(field_id);
    field_info.setFieldLength(mock_field_length);
    node->unpack_match_data(2, *md, ie, ElementIds::select_all());
    EXPECT_EQ(2, tfmd->getDocId());
    if (interleaved_features) {
        EXPECT_EQ(mock_num_occs, tfmd->getNumOccs());
        EXPECT_EQ(mock_field_length, tfmd->getFieldLength());
    } else {
        EXPECT_EQ(0, tfmd->getNumOccs());
        EXPECT_EQ(0, tfmd->getFieldLength());
    }
    EXPECT_EQ(2, tfmd->size());
    node->reset();
    node->unpack_match_data(3, *md, ie, ElementIds::select_all());
    EXPECT_EQ(2, tfmd->getDocId());
}


TEST_F(QueryTermTest, unpack_normal_match_data_for_term_node)
{
    test_unpack_match_data_for_term_node(false);
}

TEST_F(QueryTermTest, unpack_interleaved_match_data_for_term_node)
{
    test_unpack_match_data_for_term_node(true);
}
