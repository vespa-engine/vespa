// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/streaming/equiv_query_node.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/query/streaming/phrase_query_node.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::fef::MatchData;
using search::fef::SimpleTermData;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchDataPosition;
using search::query::QueryBuilder;
using search::query::Node;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::streaming::EquivQueryNode;
using search::streaming::HitList;
using search::streaming::PhraseQueryNode;
using search::streaming::Query;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

class AllowRewrite : public QueryNodeResultFactory
{
public:
    bool allow_float_terms_rewrite(vespalib::stringref) const noexcept override { return true; }
};

class EquivQueryNodeTest : public ::testing::Test
{
public:
    EquivQueryNodeTest();
    ~EquivQueryNodeTest();

    void assert_tfmd_pos(const vespalib::string label,
                         const TermFieldMatchDataPosition &tfmd_pos,
                         uint32_t exp_element_id,
                         uint32_t exp_position,
                         int32_t exp_element_weight,
                         uint32_t exp_element_length);
    vespalib::string make_simple_equiv_stack_dump();
};

EquivQueryNodeTest::EquivQueryNodeTest()
    : ::testing::Test()
{
}

EquivQueryNodeTest::~EquivQueryNodeTest() = default;

void
EquivQueryNodeTest::assert_tfmd_pos(const vespalib::string label,
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

vespalib::string
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
    return StackDumpCreator::create(*node);
}

TEST_F(EquivQueryNodeTest, test_equiv_evaluate_and_unpack)
{
    auto stack_dump = make_simple_equiv_stack_dump();
    QueryNodeResultFactory empty;
    Query q(empty, stack_dump);
    auto& eqn = dynamic_cast<EquivQueryNode&>(q.getRoot());
    auto& terms = eqn.get_terms();
    EXPECT_EQ(3, terms.size());
    for (auto& qt : terms) {
        qt->resizeFieldId(1);
    }

    // field 0
    terms[0]->add(0, 0, 1, 0);
    terms[1]->add(0, 0, 1, 1);
    terms[2]->add(0, 1, 1, 0);
    // field 1
    terms[1]->add(1, 0, 1, 4);
    terms[2]->add(1, 0, 2, 4);

    terms[0]->set_element_length(0, 10);
    terms[1]->set_element_length(0, 10);
    terms[1]->set_element_length(1, 31);
    terms[2]->set_element_length(0, 30);
    terms[2]->set_element_length(1, 31);
    HitList hits;
    eqn.evaluateHits(hits);
    auto exp_hits = HitList{{0,0,1,0},{0,0,1,1},{0,1,1,0},{1,0,2,4}};
    exp_hits[0].set_element_length(10);
    exp_hits[1].set_element_length(10);
    exp_hits[2].set_element_length(30);
    exp_hits[3].set_element_length(31);
    ASSERT_EQ(exp_hits, hits);
    EXPECT_TRUE(eqn.evaluate());

    SimpleTermData td;
    constexpr TermFieldHandle handle0 = 27;
    constexpr TermFieldHandle handle1 = 29;
    constexpr TermFieldHandle handle_max = std::max(handle0, handle1);
    td.addField(0).setHandle(handle0);
    td.addField(1).setHandle(handle1);
    terms[0]->resizeFieldId(0);
    terms[0]->getFieldInfo(0).setFieldLength(100);
    terms[1]->resizeFieldId(1);
    terms[1]->getFieldInfo(0).setFieldLength(100);
    terms[1]->getFieldInfo(1).setFieldLength(200);
    terms[2]->resizeFieldId(1);
    terms[2]->getFieldInfo(0).setFieldLength(100);
    terms[2]->getFieldInfo(1).setFieldLength(200);
    auto md = MatchData::makeTestInstance(handle_max + 1, handle_max + 1);
    auto tfmd0 = md->resolveTermField(handle0);
    auto tfmd1 = md->resolveTermField(handle1);
    tfmd0->setNeedInterleavedFeatures(true);
    tfmd1->setNeedInterleavedFeatures(true);
    eqn.unpack_match_data(2, td, *md);
    EXPECT_EQ(2, tfmd0->getDocId());
    EXPECT_EQ(3, tfmd0->getNumOccs());
    EXPECT_EQ(3, tfmd0->end() - tfmd0->begin());
    auto itr = tfmd0->begin();
    assert_tfmd_pos("tmfd0[0]", *itr, 0, 0, 1, 10);
    ++itr;
    assert_tfmd_pos("tmfd0[1]", *itr, 0, 1, 1, 10);
    ++itr;
    assert_tfmd_pos("tmfd0[2]", *itr, 1, 0, 1, 30);
    EXPECT_EQ(100, tfmd0->getFieldLength());
    EXPECT_EQ(2, tfmd1->getDocId());
    EXPECT_EQ(1, tfmd1->getNumOccs());
    EXPECT_EQ(1, tfmd1->end() - tfmd1->begin());
    itr = tfmd1->begin();
    assert_tfmd_pos("tmfd1[0]", *itr, 0, 4, 2, 31);
    EXPECT_EQ(200, tfmd1->getFieldLength());
}

TEST_F(EquivQueryNodeTest, test_equiv_flattening)
{
    auto stack_dump = make_simple_equiv_stack_dump();
    AllowRewrite allow_rewrite;
    Query q(allow_rewrite, stack_dump);
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


GTEST_MAIN_RUN_ALL_TESTS()
