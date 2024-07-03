// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/streaming/same_element_query_node.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::fef::MatchData;
using search::fef::SimpleTermData;
using search::fef::TermFieldHandle;
using search::fef::test::IndexEnvironment;
using search::query::QueryBuilder;
using search::query::Node;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::streaming::HitList;
using search::streaming::Query;
using search::streaming::QueryNode;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;
using search::streaming::SameElementQueryNode;

namespace {

class AllowRewrite : public QueryNodeResultFactory
{
public:
    explicit AllowRewrite(std::string_view index) noexcept : _allowedIndex(index) {}
    bool allow_float_terms_rewrite(std::string_view index) const noexcept override { return index == _allowedIndex; }
private:
    vespalib::string _allowedIndex;
};

}

TEST(SameElementQueryNodeTest, a_unhandled_sameElement_stack)
{
    const char * stack = "\022\002\026xyz_abcdefghij_xyzxyzxQ\001\vxxxxxx_name\034xxxxxx_xxxx_xxxxxxx_xxxxxxxxE\002\005delta\b<0.00393";
    std::string_view stackDump(stack);
    EXPECT_EQ(85u, stackDump.size());
    AllowRewrite empty("");
    const Query q(empty, stackDump);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    auto sameElement = dynamic_cast<const SameElementQueryNode *>(&root);
    EXPECT_TRUE(sameElement != nullptr);
    EXPECT_EQ(2u, sameElement->get_terms().size());
    EXPECT_EQ("xyz_abcdefghij_xyzxyzx", sameElement->getIndex());
    auto term0 = sameElement->get_terms()[0].get();
    EXPECT_TRUE(term0 != nullptr);
    auto term1 = sameElement->get_terms()[1].get();
    EXPECT_TRUE(term1 != nullptr);
}

namespace {
    void verifyQueryTermNode(const vespalib::string & index, const QueryNode *node) {
        EXPECT_TRUE(dynamic_cast<const QueryTerm *>(node) != nullptr);
        EXPECT_EQ(index, node->getIndex());
    }
}

TEST(SameElementQueryNodeTest, test_same_element_evaluate)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addSameElement(3, "field", 0, Weight(0));
    {
        builder.addStringTerm("a", "f1", 0, Weight(0));
        builder.addStringTerm("b", "f2", 1, Weight(0));
        builder.addStringTerm("c", "f3", 2, Weight(0));
    }
    Node::UP node = builder.build();
    vespalib::string stackDump = StackDumpCreator::create(*node);
    QueryNodeResultFactory empty;
    Query q(empty, stackDump);
    auto * sameElem = dynamic_cast<SameElementQueryNode *>(&q.getRoot());
    EXPECT_TRUE(sameElem != nullptr);
    EXPECT_EQ("field", sameElem->getIndex());
    EXPECT_EQ(3u, sameElem->get_terms().size());
    verifyQueryTermNode("field.f1", sameElem->get_terms()[0].get());
    verifyQueryTermNode("field.f2", sameElem->get_terms()[1].get());
    verifyQueryTermNode("field.f3", sameElem->get_terms()[2].get());

    QueryTermList leaves;
    q.getLeaves(leaves);
    EXPECT_EQ(1u, leaves.size());
    auto& terms = sameElem->get_terms();
    EXPECT_EQ(3u, terms.size());
    for (auto& qt : terms) {
        qt->resizeFieldId(3);
    }

    // field 0
    terms[0]->add(0, 0, 10, 1);
    terms[0]->add(0, 1, 20, 2);
    terms[0]->add(0, 2, 30, 3);
    terms[0]->add(0, 3, 40, 4);
    terms[0]->add(0, 4, 50, 5);
    terms[0]->add(0, 5, 60, 6);

    terms[1]->add(1, 0, 70, 7);
    terms[1]->add(1, 1, 80, 8);
    terms[1]->add(1, 2, 90, 9);
    terms[1]->add(1, 4, 100, 10);
    terms[1]->add(1, 5, 110, 11);
    terms[1]->add(1, 6, 120, 12);

    terms[2]->add(2, 0, 130, 13);
    terms[2]->add(2, 2, 140, 14);
    terms[2]->add(2, 4, 150, 15);
    terms[2]->add(2, 5, 160, 16);
    terms[2]->add(2, 6, 170, 17);
    HitList hits;
    sameElem->evaluateHits(hits);
    EXPECT_EQ(4u, hits.size());
    EXPECT_EQ(2u, hits[0].field_id());
    EXPECT_EQ(0u, hits[0].element_id());
    EXPECT_EQ(130, hits[0].element_weight());
    EXPECT_EQ(0u, hits[0].position());

    EXPECT_EQ(2u, hits[1].field_id());
    EXPECT_EQ(2u, hits[1].element_id());
    EXPECT_EQ(140, hits[1].element_weight());
    EXPECT_EQ(0u, hits[1].position());

    EXPECT_EQ(2u, hits[2].field_id());
    EXPECT_EQ(4u, hits[2].element_id());
    EXPECT_EQ(150, hits[2].element_weight());
    EXPECT_EQ(0u, hits[2].position());

    EXPECT_EQ(2u, hits[3].field_id());
    EXPECT_EQ(5u, hits[3].element_id());
    EXPECT_EQ(160, hits[3].element_weight());
    EXPECT_EQ(0u, hits[3].position());
    EXPECT_TRUE(sameElem->evaluate());

    SimpleTermData td;
    constexpr TermFieldHandle handle0 = 27;
    constexpr TermFieldHandle handle_max = handle0;
    td.addField(0).setHandle(handle0);
    auto md = MatchData::makeTestInstance(handle_max + 1, handle_max + 1);
    auto tfmd0 = md->resolveTermField(handle0);
    tfmd0->setNeedInterleavedFeatures(true);
    IndexEnvironment ie;
    sameElem->unpack_match_data(2, td, *md, ie);
    EXPECT_EQ(2, tfmd0->getDocId());
    EXPECT_EQ(0, tfmd0->getNumOccs());
    EXPECT_EQ(0, tfmd0->end() - tfmd0->begin());
}

GTEST_MAIN_RUN_ALL_TESTS()
