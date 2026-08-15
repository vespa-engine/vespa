// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/streaming/label_wrapper_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/query_term_data.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::common::ElementIds;
using search::fef::FieldInfo;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::test::IndexEnvironment;
using search::query::QueryBuilder;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::streaming::LabelWrapperQueryNode;
using search::streaming::Query;
using search::streaming::QueryTermData;
using search::streaming::QueryTermDataFactory;
using search::streaming::QueryTermList;

namespace {

constexpr uint32_t unique_id = 42;
constexpr double   label_score = 2.5;

class LabelWrapperQueryNodeTest : public testing::Test {
protected:
    QueryTermDataFactory   _factory;
    std::unique_ptr<Query> _query;

    LabelWrapperQueryNodeTest() : testing::Test(), _factory(nullptr, nullptr), _query() {}
    ~LabelWrapperQueryNodeTest() override;

    void build_query() {
        QueryBuilder<SimpleQueryNodeTypes> builder;
        builder.add_label_wrapper(unique_id, label_score);
        builder.addStringTerm("foo", "field", 1, Weight(100));
        auto build_node = builder.build();
        auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*build_node);
        _query = std::make_unique<Query>(_factory, *serializedQueryTree);
    }

    LabelWrapperQueryNode& wrapper_node() {
        std::vector<LabelWrapperQueryNode*> wrappers;
        LabelWrapperQueryNode::collect(_query->getRoot(), wrappers);
        EXPECT_EQ(1u, wrappers.size());
        return *wrappers.front();
    }
};

LabelWrapperQueryNodeTest::~LabelWrapperQueryNodeTest() = default;

TEST_F(LabelWrapperQueryNodeTest, unique_id_and_score_survive_serialization) {
    build_query();
    auto& node = wrapper_node();
    EXPECT_EQ(unique_id, node.unique_id());
    EXPECT_EQ(label_score, node.label_score());
}

TEST_F(LabelWrapperQueryNodeTest, score_is_unpacked_as_the_raw_score_of_the_no_field_handle) {
    build_query();
    auto& node = wrapper_node();

    // Set up like RankProcessor does: a single handle for the reserved "no field"
    auto&           td = static_cast<QueryTermData&>(node.getQueryItem()).getTermData();
    MatchDataLayout mdl;
    auto            field_id = FieldInfo::no_field().id();
    TermFieldHandle handle = mdl.allocTermField(field_id);
    td.addField(field_id).setHandle(handle);

    QueryTermList terms;
    _query->getLeaves(terms);
    ASSERT_EQ(1u, terms.size());

    auto             md = mdl.createMatchData();
    auto*            tfmd = md->resolveTermField(handle);
    IndexEnvironment index_env;

    // The child does not match, so neither does the wrapper
    EXPECT_FALSE(node.evaluate());
    node.unpack_match_data(1, *md, index_env, ElementIds::select_all());
    EXPECT_TRUE(tfmd->has_invalid_docid());

    // Once the child matches, the score shows up as the raw score
    node.reset();
    terms[0]->add(0, 0, 1, 0);
    EXPECT_TRUE(node.evaluate());
    node.unpack_match_data(2, *md, index_env, ElementIds::select_all());
    EXPECT_TRUE(tfmd->has_ranking_data(2));
    EXPECT_EQ(label_score, tfmd->getRawScore());
}

} // namespace
