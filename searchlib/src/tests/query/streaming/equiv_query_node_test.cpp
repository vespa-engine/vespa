// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/query/streaming/equiv_query_node.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/streaming/phrase_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::common::ElementIds;
using search::fef::MatchData;
using search::fef::SimpleTermData;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchDataPosition;
using search::fef::test::IndexEnvironment;
using search::query::QueryBuilder;
using search::query::Node;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::SerializedQueryTreeSP;
using search::streaming::EquivQueryNode;
using search::streaming::HitList;
using search::streaming::PhraseQueryNode;
using search::streaming::Query;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

inline namespace equiv_query_node_test {

class AllowRewrite : public QueryNodeResultFactory
{
public:
    bool allow_float_terms_rewrite(std::string_view) const noexcept override { return true; }
};

}

class EquivQueryNodeTest : public ::testing::Test
{
public:
    EquivQueryNodeTest();
    ~EquivQueryNodeTest() override;

    void assert_tfmd_pos(const std::string label,
                         const TermFieldMatchDataPosition &tfmd_pos,
                         uint32_t exp_element_id,
                         uint32_t exp_position,
                         int32_t exp_element_weight,
                         uint32_t exp_element_length);
    SerializedQueryTreeSP make_simple_equiv_stack_dump();
};

EquivQueryNodeTest::EquivQueryNodeTest()
    : ::testing::Test()
{
}

EquivQueryNodeTest::~EquivQueryNodeTest() = default;

void
EquivQueryNodeTest::assert_tfmd_pos(const std::string label,
                                    const TermFieldMatchDataPosition &tfmd_pos,
                                    uint32_t exp_element_id,
                                    uint32_t exp_position,
                                    int32_t exp_element_weight,
                                    uint32_t exp_element_length)
{
    SCOPED_TRACE(label);
    EXPECT_EQ(exp_element_id, tfmd_pos.getElementId());
    EXPECT_EQ(exp_position, tfmd_pos.getPosition());
    EXPECT_EQ(exp_element_weight, tfmd_pos.getElementWeight());
    EXPECT_EQ(exp_element_length, tfmd_pos.getElementLen());
}

SerializedQueryTreeSP
EquivQueryNodeTest::make_simple_equiv_stack_dump()
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addEquiv(3, 0, Weight(0));
    {
        builder.addStringTerm("2", "", 0, Weight(0));
        builder.addStringTerm("2.5", "", 0, Weight(0));
        builder.addStringTerm("3", "", 0, Weight(0));
    }
    Node::UP node = builder.build();
    return StackDumpCreator::createSerializedQueryTree(*node);
}

TEST_F(EquivQueryNodeTest, test_equiv_evaluate_and_unpack)
{
    auto serializedQueryTree = make_simple_equiv_stack_dump();
    QueryNodeResultFactory empty;
    Query q(empty, *serializedQueryTree);
    auto& eqn = dynamic_cast<EquivQueryNode&>(q.getRoot());
    auto& terms = eqn.get_terms();
    EXPECT_EQ(3, terms.size());
    for (auto& qt : terms) {
        qt->resizeFieldId(1);
    }

    /*
     * Populate hit lists in query terms, emulating the result of
     * having performed a streaming search.
     */
    constexpr uint32_t field0 = 0;
    constexpr uint32_t field1 = 1;
    constexpr uint32_t elem0 = 0;
    constexpr uint32_t elem1 = 1;
    constexpr int32_t weight1 = 1;
    constexpr int32_t weight2 = 2;
    constexpr uint32_t pos5 = 5;
    constexpr uint32_t pos6 = 6;
    constexpr uint32_t pos3 = 3;
    constexpr uint32_t pos4 = 4;
    constexpr uint32_t field0_len = 100;
    constexpr uint32_t field1_len = 200;
    constexpr uint32_t field0_element0_len = 10;
    constexpr uint32_t field0_element1_len = 30;
    constexpr uint32_t field1_element0_len = 31;
    // field 0
    terms[0]->add(field0, elem0, weight1, pos5);
    terms[1]->add(field0, elem0, weight1, pos6);
    terms[2]->add(field0, elem1, weight1, pos3);
    // field 1
    terms[1]->add(field1, elem0, weight1, pos4);
    terms[2]->add(field1, elem0, weight2, pos4);

    terms[0]->set_element_length(0, field0_element0_len);
    terms[1]->set_element_length(0, field0_element0_len);
    terms[1]->set_element_length(1, field1_element0_len);
    terms[2]->set_element_length(0, field0_element1_len);
    terms[2]->set_element_length(1, field1_element0_len);

    /*
     * evaluateHits() should get the union of the hits for each query term
     * but without duplicates.
     */
    HitList hits;
    eqn.evaluateHits(hits);
    auto exp_hits = HitList{{field0,elem0,weight1,pos5},{field0,elem0,weight1,pos6},{field0,elem1,weight1,pos3},{field1,elem0,weight2,pos4}};
    exp_hits[0].set_element_length(field0_element0_len);
    exp_hits[1].set_element_length(field0_element0_len);
    exp_hits[2].set_element_length(field0_element1_len);
    exp_hits[3].set_element_length(field1_element0_len);
    ASSERT_EQ(exp_hits, hits);
    EXPECT_TRUE(eqn.evaluate());

    /*
     * Verify that unpack_match_data() gives the expected term field
     * match data information.
     */
    SimpleTermData td;
    constexpr TermFieldHandle handle0 = 27;
    constexpr TermFieldHandle handle1 = 29;
    constexpr TermFieldHandle handle_max = std::max(handle0, handle1);
    td.addField(0).setHandle(handle0);
    td.addField(1).setHandle(handle1);
    terms[0]->resizeFieldId(field0);
    terms[0]->getFieldInfo(field0).setFieldLength(field0_len);
    terms[1]->resizeFieldId(field1);
    terms[1]->getFieldInfo(field0).setFieldLength(field0_len);
    terms[1]->getFieldInfo(field1).setFieldLength(field1_len);
    terms[2]->resizeFieldId(field1);
    terms[2]->getFieldInfo(field0).setFieldLength(field0_len);
    terms[2]->getFieldInfo(field1).setFieldLength(field1_len);
    auto md = MatchData::makeTestInstance(handle_max + 1, handle_max + 1);
    auto tfmd0 = md->resolveTermField(handle0);
    auto tfmd1 = md->resolveTermField(handle1);
    tfmd0->setNeedInterleavedFeatures(true);
    tfmd1->setNeedInterleavedFeatures(true);
    IndexEnvironment ie;
    eqn.unpack_match_data(2, td, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd0->has_ranking_data(2));
    EXPECT_EQ(3, tfmd0->getNumOccs());
    EXPECT_EQ(3, tfmd0->end() - tfmd0->begin());
    auto itr = tfmd0->begin();
    assert_tfmd_pos("tmfd0[0]", *itr, elem0, pos5, weight1, field0_element0_len);
    ++itr;
    assert_tfmd_pos("tmfd0[1]", *itr, elem0, pos6, weight1, field0_element0_len);
    ++itr;
    assert_tfmd_pos("tmfd0[2]", *itr, elem1, pos3, weight1, field0_element1_len);
    EXPECT_EQ(field0_len, tfmd0->getFieldLength());
    EXPECT_TRUE(tfmd1->has_ranking_data(2));
    EXPECT_EQ(1, tfmd1->getNumOccs());
    EXPECT_EQ(1, tfmd1->end() - tfmd1->begin());
    itr = tfmd1->begin();
    assert_tfmd_pos("tmfd1[0]", *itr, elem0, pos4, weight2, field1_element0_len);
    EXPECT_EQ(field1_len, tfmd1->getFieldLength());
    std::vector<uint32_t> element_ids;
    eqn.get_element_ids(element_ids);
    EXPECT_EQ((std::vector<uint32_t>{ 0, 1 }), element_ids);
}

TEST_F(EquivQueryNodeTest, test_equiv_flattening)
{
    auto serializedQueryTree = make_simple_equiv_stack_dump();
    AllowRewrite allow_rewrite;
    Query q(allow_rewrite, *serializedQueryTree);
    auto& eqn = dynamic_cast<EquivQueryNode&>(q.getRoot());
    auto& terms = eqn.get_terms();
    // Query is flattened to equiv("2", "2.5", phrase("2","5"), "3")
    EXPECT_EQ(4, terms.size());
    EXPECT_EQ("2", terms[0]->getTermString());
    EXPECT_EQ("2.5", terms[1]->getTermString());
    auto phrase = dynamic_cast<PhraseQueryNode*>(terms[2].get());
    EXPECT_NE(phrase, nullptr);
    EXPECT_EQ(2, phrase->get_terms().size());
    EXPECT_EQ("2", phrase->get_terms()[0]->getTermString());
    EXPECT_EQ("5", phrase->get_terms()[1]->getTermString());
    EXPECT_EQ("3", terms[3]->getTermString());
}
