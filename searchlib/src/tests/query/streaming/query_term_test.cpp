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
#include <vespa/searchlib/queryeval/element_id_extractor.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::common::ElementIds;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::MatchData;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::fef::test::IndexEnvironment;
using search::index::schema::CollectionType;
using search::index::schema::DataType;
using search::query::Weight;
using search::query::QueryBuilder;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::queryeval::ElementIdExtractor;
using search::streaming::Query;
using search::streaming::QueryTerm;
using search::streaming::QueryTermData;
using search::streaming::QueryTermDataFactory;
using search::streaming::QueryTermList;

namespace {

std::vector<uint32_t>
make_vec(std::vector<uint32_t> values)
{
    return values;
}

}

class QueryTermTest : public testing::Test
{
protected:
    QueryTermDataFactory       _factory;
    IndexEnvironment           _index_env;
    std::unique_ptr<Query>     _query;
    uint32_t                   _field_id;
    QueryTerm*                 _node;
    std::unique_ptr<MatchData> _md;
    TermFieldMatchData*        _tfmd;

    QueryTermTest();
    ~QueryTermTest() override;

    static constexpr TermFieldHandle handle = 27;
    static constexpr uint32_t mock_num_occs = 4;
    static constexpr uint32_t mock_field_length = 101;

    void build_query(QueryBuilder<SimpleQueryNodeTypes>& builder);
    void build_query(bool filter);
    void populate_term();
    void reset_tfmd() { _tfmd->resetOnlyDocId(TermFieldMatchData::invalidId()); }
    std::vector<uint32_t> extract_element_ids(uint32_t docid);
    void test_unpack_match_data_for_term_node(bool interleaved_features, bool filter);
};

QueryTermTest::QueryTermTest()
    : testing::Test(),
      _factory(nullptr, nullptr),
      _index_env(),
      _query(),
      _field_id(0u),
      _node(nullptr),
      _md(),
      _tfmd(nullptr)
{
    FieldInfo field(FieldType::INDEX, CollectionType::ARRAY, "field", 12);
    FieldInfo filterfield(FieldType::INDEX, CollectionType::ARRAY, "filterfield", 13);
    filterfield.setFilter(true);
    auto& fields = _index_env.getFields();
    for (uint32_t id = 0; id < field.id(); ++id) {
        fields.emplace_back(FieldType::INDEX, CollectionType::SINGLE, "dummy" + std::to_string(id), id);
    }
    _index_env.getFields().emplace_back(field);
    _index_env.getFields().emplace_back(filterfield);
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
QueryTermTest::build_query(bool filter)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    constexpr int32_t id = 42;
    constexpr int32_t weight = 1;
    builder.addStringTerm("term", filter ? "filterfield" : "field", id, Weight(weight));
    build_query(builder);
    QueryTermList term_list;
    _query->getLeaves(term_list);
    ASSERT_EQ(1u, term_list.size());
    _node = dynamic_cast<QueryTerm *>(term_list.front());
    ASSERT_NE(nullptr, _node);
    auto &qtd = static_cast<QueryTermData &>(_node->getQueryItem());
    auto &td = qtd.getTermData();
    _field_id = filter ? 13 : 12;
    td.addField(_field_id).setHandle(handle);
    _node->resizeFieldId(_field_id);
    _md = MatchData::makeTestInstance(handle + 1, handle + 1);
    _tfmd = _md->resolveTermField(handle);
    ASSERT_NE(nullptr, _tfmd);
}

void
QueryTermTest::populate_term()
{
    ASSERT_NE(nullptr, _node);
    _node->add(_field_id, 0, 1, 0);
    _node->add(_field_id, 3, 1, 1);
    _node->add(_field_id, 7, 1, 1);
    _node->add(_field_id, 10, 1, 1);
    auto& field_info = _node->getFieldInfo(_field_id);
    field_info.setFieldLength(mock_field_length);

}

std::vector<uint32_t>
QueryTermTest::extract_element_ids(uint32_t docid)
{
    std::vector<uint32_t> element_ids;
    ElementIdExtractor::get_element_ids(*_tfmd, docid, element_ids);
    return element_ids;
}

void
QueryTermTest::test_unpack_match_data_for_term_node(bool interleaved_features, bool filter)
{
    ASSERT_NO_FATAL_FAILURE(build_query(filter));
    _tfmd->setNeedInterleavedFeatures(interleaved_features);
    EXPECT_TRUE(_tfmd->has_invalid_docid());
    _node->unpack_match_data(1, *_md, _index_env, ElementIds::select_all());
    EXPECT_TRUE(_tfmd->has_invalid_docid());
    ASSERT_NO_FATAL_FAILURE(populate_term());
    _node->unpack_match_data(2, *_md, _index_env, ElementIds::select_all());
    EXPECT_TRUE(_tfmd->has_ranking_data(2));
    if (interleaved_features && !filter) {
        EXPECT_EQ(mock_num_occs, _tfmd->getNumOccs());
        EXPECT_EQ(mock_field_length, _tfmd->getFieldLength());
    } else {
        EXPECT_EQ(0, _tfmd->getNumOccs());
        EXPECT_EQ(0, _tfmd->getFieldLength());
    }
    EXPECT_EQ(filter ? 0 : mock_num_occs, _tfmd->size());
    _node->reset();
    _node->unpack_match_data(3, *_md, _index_env, ElementIds::select_all());
    EXPECT_TRUE(_tfmd->has_ranking_data(2));
}

TEST_F(QueryTermTest, unpack_normal_match_data_for_term_node)
{
    test_unpack_match_data_for_term_node(false, false);
}

TEST_F(QueryTermTest, unpack_interleaved_match_data_for_term_node)
{
    test_unpack_match_data_for_term_node(true, false);
}

TEST_F(QueryTermTest, unpack_normal_match_data_for_term_node_filter)
{
    test_unpack_match_data_for_term_node(false, true);
}

TEST_F(QueryTermTest, unpack_interleaved_match_data_for_term_node_filter)
{
    test_unpack_match_data_for_term_node(true, true);
}

TEST_F(QueryTermTest, unpack_match_data_with_element_filter)
{
    ASSERT_NO_FATAL_FAILURE(build_query(false));
    _tfmd->setNeedInterleavedFeatures(true);
    ASSERT_NO_FATAL_FAILURE(populate_term());
    constexpr uint32_t docid = 2;
    _node->unpack_match_data(docid, *_md, _index_env, ElementIds::select_all());
    EXPECT_TRUE(_tfmd->has_ranking_data(docid));
    EXPECT_EQ(mock_num_occs, _tfmd->getNumOccs());
    EXPECT_EQ(mock_field_length, _tfmd->getFieldLength());
    EXPECT_EQ(mock_num_occs, _tfmd->size());
    EXPECT_EQ(make_vec({0, 3, 7, 10}), extract_element_ids(docid));
    reset_tfmd();
    _node->unpack_match_data(docid, *_md, _index_env, ElementIds(make_vec({0, 2, 3, 8, 10, 12})));
    EXPECT_TRUE(_tfmd->has_ranking_data(docid));
    EXPECT_EQ(3, _tfmd->getNumOccs());
    EXPECT_EQ(mock_field_length, _tfmd->getFieldLength());
    EXPECT_EQ(3, _tfmd->size());
    EXPECT_EQ(make_vec({0, 3, 10}), extract_element_ids(docid));
    reset_tfmd();
    _node->unpack_match_data(docid, *_md, _index_env, ElementIds(make_vec({3})));
    EXPECT_TRUE(_tfmd->has_ranking_data(docid));
    EXPECT_EQ(1, _tfmd->getNumOccs());
    EXPECT_EQ(mock_field_length, _tfmd->getFieldLength());
    EXPECT_EQ(1, _tfmd->size());
    EXPECT_EQ(make_vec({3}), extract_element_ids(docid));
    reset_tfmd();
    _node->unpack_match_data(docid, *_md, _index_env, ElementIds(make_vec({4})));
    EXPECT_TRUE(_tfmd->has_invalid_docid());
    EXPECT_EQ(0, _tfmd->getNumOccs());
    EXPECT_EQ(0, _tfmd->getFieldLength());
    EXPECT_EQ(0, _tfmd->size());
    EXPECT_EQ(make_vec({}), extract_element_ids(docid));
}
