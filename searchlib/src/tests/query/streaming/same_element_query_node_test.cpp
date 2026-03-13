// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/engine/search_protocol_proto.h>
#include <vespa/searchlib/query/streaming/same_element_query_node.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/query_builder.h>
#include <vespa/searchlib/query/streaming/query_term_data.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/query_to_protobuf.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/queryeval/element_id_extractor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>

using search::common::ElementIds;
using search::fef::MatchData;
using search::fef::SimpleTermData;
using search::fef::TermFieldHandle;
using search::fef::test::IndexEnvironment;
using search::query::QueryBuilder;
using search::query::QueryToProtobuf;
using search::query::Node;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::queryeval::ElementIdExtractor;
using search::SerializedQueryTree;
using search::streaming::HitList;
using search::streaming::Query;
using search::streaming::QueryNode;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTerm;
using search::streaming::QueryTermData;
using search::streaming::QueryTermDataFactory;
using search::streaming::QueryTermList;
using search::streaming::SameElementQueryNode;
using searchlib::searchprotocol::protobuf::QueryTree;

namespace {

class AllowRewrite : public QueryNodeResultFactory
{
public:
    explicit AllowRewrite(std::string_view index) noexcept : _allowedIndex(index) {}
    ~AllowRewrite() override;
    bool allow_float_terms_rewrite(std::string_view index) const noexcept override { return index == _allowedIndex; }
private:
    std::string _allowedIndex;
};

AllowRewrite::~AllowRewrite() = default;

}

class SameElementQueryNodeTest : public ::testing::Test {
protected:
    enum class QueryTweak {
        NORMAL,       // All children of query root are term nodes
        AND,          // Last child is AND with two term nodes
        OR,           // Last child is OR with two term nodes
        ANDNOT,       // Last child is ANDNOT with two term nodes
        RANK          // Last child is RANK with two term nodes
    };

    SameElementQueryNodeTest();
    ~SameElementQueryNodeTest() override;
    static bool evaluate_query(QueryTweak query_tweak, const std::vector<std::vector<uint32_t>>& elementsvv, std::vector<uint32_t> element_filter = std::vector<uint32_t>());
    static std::vector<uint32_t> get_element_ids(QueryTweak query_tweak, const std::vector<std::vector<uint32_t>>& elementsvv, std::vector<uint32_t> element_filter = std::vector<uint32_t>());
    std::vector<std::vector<uint32_t>> extract_element_ids(QueryTweak query_tweak,
                                                           const std::vector<std::vector<uint32_t>>& elementsvv,
                                                           std::vector<uint32_t> element_filter = std::vector<uint32_t>());
    static std::unique_ptr<Query> make_query(QueryTweak query_tweak,
                                             const std::vector<std::vector<uint32_t>>& elementsvv,
                                             std::vector<uint32_t> element_filter = std::vector<uint32_t>());
};

SameElementQueryNodeTest::SameElementQueryNodeTest()
    : testing::Test()
{
}

SameElementQueryNodeTest::~SameElementQueryNodeTest() = default;

bool
SameElementQueryNodeTest::evaluate_query(QueryTweak query_tweak, const std::vector<std::vector<uint32_t>>& elementsvv, std::vector<uint32_t> element_filter)
{
    auto query = make_query(query_tweak, elementsvv, std::move(element_filter));
    return query->getRoot().evaluate();
}

std::vector<uint32_t>
SameElementQueryNodeTest::get_element_ids(QueryTweak query_tweak, const std::vector<std::vector<uint32_t>>& elementsvv, std::vector<uint32_t> element_filter)
{
    auto query = make_query(query_tweak, elementsvv, std::move(element_filter));
    std::vector<uint32_t> result;
    query->getRoot().get_element_ids(result);
    return result;
}

std::vector<std::vector<uint32_t>>
SameElementQueryNodeTest::extract_element_ids(QueryTweak query_tweak, const std::vector<std::vector<uint32_t>>& elementsvv, std::vector<uint32_t> element_filter)
{
    auto query = make_query(query_tweak, elementsvv, std::move(element_filter));
    auto md = MatchData::makeTestInstance(elementsvv.size(), 1);
    constexpr uint32_t docid = 2;
    IndexEnvironment index_env;
    query->getRoot().unpack_match_data(docid, *md, index_env, ElementIds::select_all());
    std::vector<std::vector<uint32_t>> result;
    for (uint32_t idx = 0; idx < elementsvv.size(); ++idx) {
        result.emplace_back();
        auto* tfmd = md->resolveTermField(idx);
        ElementIdExtractor::get_element_ids(*tfmd, docid, result.back());
    }
    return result;
}

std::unique_ptr<Query>
SameElementQueryNodeTest::make_query(QueryTweak query_tweak,
                                     const std::vector<std::vector<uint32_t>>& elementsvv,
                                     std::vector<uint32_t> element_filter)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    auto num_terms = elementsvv.size();
    auto top_arity = num_terms;
    switch (query_tweak) {
        case QueryTweak::AND:
        case QueryTweak::OR:
        case QueryTweak::ANDNOT:
        case QueryTweak::RANK:
            EXPECT_LE(2, num_terms);
            assert(num_terms >= 2);
            --top_arity;
            break;
        default:
            break;
    }
    builder.addSameElement(top_arity, "field", 0, Weight(0), std::move(element_filter));
    for (uint32_t idx = 0; idx < elementsvv.size(); ++idx) {
        switch (query_tweak) {
            case QueryTweak::AND:
                if (idx == elementsvv.size() - 2) {
                    builder.addAnd(2);
                }
                break;
            case QueryTweak::OR:
                if (idx == elementsvv.size() - 2) {
                    builder.addOr(2);
                }
                break;
            case QueryTweak::ANDNOT:
                if (idx == elementsvv.size() - 2) {
                    builder.addAndNot(2);
                }
                break;
            case QueryTweak::RANK:
                if (idx == elementsvv.size() - 2) {
                    builder.addRank(2);
                }
                break;
            default:
                break;
        }
        vespalib::asciistream s;
        s << "s" << idx;
        builder.addStringTerm(s.str(), "", idx, Weight(0));
    }
    auto node = builder.build();
    QueryToProtobuf qtp;
    auto serializedQueryTree = SerializedQueryTree::fromProtobuf(std::make_unique<QueryTree>(qtp.serialize(*node)));
    QueryTermDataFactory empty(nullptr, nullptr);
    auto q = std::make_unique<Query>(empty, *serializedQueryTree);
    auto& top = dynamic_cast<SameElementQueryNode&>(q->getRoot());
    EXPECT_EQ(top_arity, top.get_children().size());
    constexpr uint32_t field_id = 0;
    top.resizeFieldId(field_id);
    QueryTermList terms;
    top.get_hidden_leaves(terms);
    EXPECT_EQ(elementsvv.size(), terms.size());
    for (QueryTerm * qt : terms) {
        qt->resizeFieldId(field_id);
    }
    constexpr uint32_t pos = 0;
    constexpr int32_t element_weight = 10;
    constexpr uint32_t element_len = 5;
    for (uint32_t idx = 0; idx < elementsvv.size(); ++idx) {
        auto& elementsv = elementsvv[idx];
        auto* term = terms[idx];
        if (idx + 1 == elementsvv.size() && query_tweak == QueryTweak::ANDNOT) {
            EXPECT_FALSE(term->isRanked());
        } else {
            EXPECT_NE(top.isRanked(), term->isRanked());
        }
        auto& qtd = static_cast<QueryTermData &>(term->getQueryItem());
        auto& td = qtd.getTermData();
        td.addField(field_id).setHandle(idx);
        for (auto& element : elementsv) {
            auto hl_idx = term->add(field_id, element, element_weight, pos);
            term->set_element_length(hl_idx, element_len);
        }
    }
    return q;
}

TEST_F(SameElementQueryNodeTest, a_unhandled_sameElement_stack)
{
    const char * stack = "\022\002\026xyz_abcdefghij_xyzxyzxQ\001\vxxxxxx_name\034xxxxxx_xxxx_xxxxxxx_xxxxxxxxE\002\005delta\b<0.00393";
    std::string_view stackDump(stack);
    EXPECT_EQ(85u, stackDump.size());
    auto serializedQueryTree = SerializedQueryTree::fromStackDump(stackDump);
    AllowRewrite empty("");
    const Query q(empty, *serializedQueryTree);
    EXPECT_TRUE(q.valid());
    const QueryNode & root = q.getRoot();
    auto sameElement = dynamic_cast<const SameElementQueryNode *>(&root);
    EXPECT_TRUE(sameElement != nullptr);
    EXPECT_EQ(2u, sameElement->get_children().size());
    EXPECT_EQ("xyz_abcdefghij_xyzxyzx", sameElement->getIndex());
    auto child0 = sameElement->get_children()[0].get();
    EXPECT_TRUE(child0 != nullptr);
    auto child1 = sameElement->get_children()[1].get();
    EXPECT_TRUE(child1 != nullptr);
}

namespace {
    void verifyQueryTermNode(const std::string & index, const QueryNode *node) {
        EXPECT_TRUE(dynamic_cast<const QueryTerm *>(node) != nullptr);
        EXPECT_EQ(index, node->getIndex());
    }
}

TEST_F(SameElementQueryNodeTest, test_same_element_evaluate)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    builder.addSameElement(3, "field", 0, Weight(0));
    {
        builder.addStringTerm("a", "f1", 0, Weight(0));
        builder.addStringTerm("b", "f2", 1, Weight(0));
        builder.addStringTerm("c", "f3", 2, Weight(0));
    }
    Node::UP node = builder.build();
    QueryToProtobuf qtp;
    auto serializedQueryTree = SerializedQueryTree::fromProtobuf(std::make_unique<QueryTree>(qtp.serialize(*node)));
    QueryNodeResultFactory empty;
    Query q(empty, *serializedQueryTree);
    auto * sameElem = dynamic_cast<SameElementQueryNode *>(&q.getRoot());
    EXPECT_TRUE(sameElem != nullptr);
    EXPECT_EQ("field", sameElem->getIndex());
    EXPECT_EQ(3u, sameElem->get_children().size());
    verifyQueryTermNode("field.f1", sameElem->get_children()[0].get());
    verifyQueryTermNode("field.f2", sameElem->get_children()[1].get());
    verifyQueryTermNode("field.f3", sameElem->get_children()[2].get());

    QueryTermList leaves;
    q.getLeaves(leaves);
    EXPECT_EQ(1u, leaves.size());
    QueryTermList terms;
    sameElem->get_hidden_leaves(terms);
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
    EXPECT_TRUE(hits.empty());
    std::vector<uint32_t> element_ids;
    sameElem->get_element_ids(element_ids);
    EXPECT_EQ((std::vector<uint32_t>{ 0, 2, 4, 5}), element_ids);
    EXPECT_TRUE(sameElem->evaluate());

    SimpleTermData td;
    constexpr TermFieldHandle handle0 = 27;
    constexpr TermFieldHandle handle_max = handle0;
    td.addField(0).setHandle(handle0);
    auto md = MatchData::makeTestInstance(handle_max + 1, handle_max + 1);
    auto tfmd0 = md->resolveTermField(handle0);
    tfmd0->setNeedInterleavedFeatures(true);
    IndexEnvironment ie;
    sameElem->unpack_match_data(2, td, *md, ie, ElementIds::select_all());
    EXPECT_TRUE(tfmd0->has_ranking_data(2));
    EXPECT_EQ(0, tfmd0->getNumOccs());
    EXPECT_EQ(0, tfmd0->end() - tfmd0->begin());
}

TEST_F(SameElementQueryNodeTest, and_below_same_element)
{
    std::vector<std::vector<uint32_t>> elementsvv3({ { 5, 7, 10, 12 }, { 4, 7, 12, 14} });
    std::vector<std::vector<uint32_t>> elementsvv9({ { 4, 6, 9, 10 }, { 3, 9, 13 } });
    EXPECT_TRUE(evaluate_query(QueryTweak::AND, elementsvv3));
    EXPECT_EQ((std::vector<uint32_t>{ 7, 12 }), get_element_ids(QueryTweak::AND, elementsvv3));
    EXPECT_TRUE(evaluate_query(QueryTweak::AND, elementsvv9));
    EXPECT_EQ((std::vector<uint32_t>{ 9 }), get_element_ids(QueryTweak::AND, elementsvv9));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 7, 12 }, { 7, 12 } }),
              extract_element_ids(QueryTweak::AND, elementsvv3));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 9 }, { 9 } }),
              extract_element_ids(QueryTweak::AND, elementsvv9));
}

TEST_F(SameElementQueryNodeTest, or_below_same_element)
{
    std::vector<std::vector<uint32_t>> elementsvv3({ { 5, 10 }, { 7, 12 } });
    std::vector<std::vector<uint32_t>> elementsvv9({ { 6 }, { 4, 9 } });
    EXPECT_TRUE(evaluate_query(QueryTweak::OR, elementsvv3));
    EXPECT_EQ((std::vector<uint32_t>{ 5, 7, 10, 12 }), get_element_ids(QueryTweak::OR, elementsvv3));
    EXPECT_TRUE(evaluate_query(QueryTweak::OR, elementsvv9));
    EXPECT_EQ((std::vector<uint32_t>{ 4, 6, 9 }), get_element_ids(QueryTweak::OR, elementsvv9));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 5, 10 }, { 7, 12 } }),
              extract_element_ids(QueryTweak::OR, elementsvv3));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 6 }, { 4, 9 } }),
              extract_element_ids(QueryTweak::OR, elementsvv9));
}

TEST_F(SameElementQueryNodeTest, and_not_below_same_element)
{
    std::vector<std::vector<uint32_t>> elementsvv3({ { 5, 7, 10, 12 }, { 7, 12 } });
    std::vector<std::vector<uint32_t>> elementsvv5({ { 7, 12 }, { 5, 7, 10, 12 } });
    std::vector<std::vector<uint32_t>> elementsvv9({ { 4, 6, 9 }, { 4, 9 } });
    EXPECT_TRUE(evaluate_query(QueryTweak::ANDNOT, elementsvv3));
    EXPECT_EQ((std::vector<uint32_t>{ 5, 10 }), get_element_ids(QueryTweak::ANDNOT, elementsvv3));
    EXPECT_FALSE(evaluate_query(QueryTweak::ANDNOT, elementsvv5));
    EXPECT_EQ((std::vector<uint32_t>{}), get_element_ids(QueryTweak::ANDNOT, elementsvv5));
    EXPECT_TRUE(evaluate_query(QueryTweak::ANDNOT, elementsvv9));
    EXPECT_EQ((std::vector<uint32_t>{ 6 }), get_element_ids(QueryTweak::ANDNOT, elementsvv9));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 5, 10 }, {} }),
              extract_element_ids(QueryTweak::ANDNOT, elementsvv3));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ {}, {} }),
              extract_element_ids(QueryTweak::ANDNOT, elementsvv5));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 6 }, {} }),
              extract_element_ids(QueryTweak::ANDNOT, elementsvv9));
}

TEST_F(SameElementQueryNodeTest, rank_below_same_element)
{
    std::vector<std::vector<uint32_t>> elementsvv3({ { 5, 7, 10, 12 }, { 7, 12 } });
    std::vector<std::vector<uint32_t>> elementsvv5({ { 7, 12 }, { 5, 7, 10, 12 } });
    std::vector<std::vector<uint32_t>> elementsvv9({ { 4, 6, 9 }, { 4, 9 } });
    EXPECT_TRUE(evaluate_query(QueryTweak::RANK, elementsvv3));
    EXPECT_EQ((std::vector<uint32_t>{ 5, 7, 10, 12 }), get_element_ids(QueryTweak::RANK, elementsvv3));
    EXPECT_TRUE(evaluate_query(QueryTweak::RANK, elementsvv5));
    EXPECT_EQ((std::vector<uint32_t>{7, 12}), get_element_ids(QueryTweak::RANK, elementsvv5));
    EXPECT_TRUE(evaluate_query(QueryTweak::RANK, elementsvv9));
    EXPECT_EQ((std::vector<uint32_t>{ 4, 6, 9 }), get_element_ids(QueryTweak::RANK, elementsvv9));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 5, 7, 10,12 }, { 7, 12 } }),
              extract_element_ids(QueryTweak::RANK, elementsvv3));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 7, 12 }, { 7, 12 } }),
              extract_element_ids(QueryTweak::RANK, elementsvv5));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 4, 6,9 }, { 4, 9 } }),
              extract_element_ids(QueryTweak::RANK, elementsvv9));
}

TEST_F(SameElementQueryNodeTest, element_filter_simple)
{
    std::vector<std::vector<uint32_t>> elementsvv({ { 5, 7, 10, 12 }, { 4, 7, 12, 14} });

    std::vector<uint32_t> element_filter({ 5 });
    EXPECT_FALSE(evaluate_query(QueryTweak::NORMAL, elementsvv, element_filter));
    EXPECT_EQ((std::vector<uint32_t>{}), get_element_ids(QueryTweak::NORMAL, elementsvv, element_filter));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { }, { } }),
              extract_element_ids(QueryTweak::NORMAL, elementsvv, element_filter));

    std::vector<uint32_t> element_filter2({ 7 });
    EXPECT_TRUE(evaluate_query(QueryTweak::NORMAL, elementsvv, element_filter2));
    EXPECT_EQ((std::vector<uint32_t>{ 7 }), get_element_ids(QueryTweak::NORMAL, elementsvv, element_filter2));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 7 }, { 7 } }),
              extract_element_ids(QueryTweak::NORMAL, elementsvv, element_filter2));
}

TEST_F(SameElementQueryNodeTest, element_filter_with_multiple_ids)
{
    std::vector<std::vector<uint32_t>> elementsvv({ { 5, 7, 10, 12 }, { 4, 7, 12, 14} });
    std::vector<uint32_t> element_filter({ 4, 5, 6, 7, 9, 10, 12, 13 });
    EXPECT_TRUE(evaluate_query(QueryTweak::NORMAL, elementsvv, element_filter));
    EXPECT_EQ((std::vector<uint32_t>{ 7, 12 }), get_element_ids(QueryTweak::NORMAL, elementsvv, element_filter));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 7, 12 }, { 7, 12 } }),
              extract_element_ids(QueryTweak::NORMAL, elementsvv, element_filter));
}

TEST_F(SameElementQueryNodeTest, element_filter_for_indexing)
{
    std::vector<std::vector<uint32_t>> elementsvv({ { 4, 7, 12, 14} });

    std::vector<uint32_t> element_filter({ 4 });
    EXPECT_TRUE(evaluate_query(QueryTweak::NORMAL, elementsvv, element_filter));
    EXPECT_EQ((std::vector<uint32_t>{ 4 }), get_element_ids(QueryTweak::NORMAL, elementsvv, element_filter));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 4 } }),
              extract_element_ids(QueryTweak::NORMAL, elementsvv, element_filter));

    std::vector<uint32_t> element_filter2({ 5 });
    EXPECT_FALSE(evaluate_query(QueryTweak::NORMAL, elementsvv, element_filter2));
    EXPECT_EQ((std::vector<uint32_t>{ }), get_element_ids(QueryTweak::NORMAL, elementsvv, element_filter2));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { } }),
              extract_element_ids(QueryTweak::NORMAL, elementsvv, element_filter2));

    std::vector<uint32_t> element_filter3({ 3, 14 });
    EXPECT_TRUE(evaluate_query(QueryTweak::NORMAL, elementsvv, element_filter3));
    EXPECT_EQ((std::vector<uint32_t>{ 14 }), get_element_ids(QueryTweak::NORMAL, elementsvv, element_filter3));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { 14 } }),
              extract_element_ids(QueryTweak::NORMAL, elementsvv, element_filter3));

    std::vector<uint32_t> element_filter4({ 3, 13 });
    EXPECT_FALSE(evaluate_query(QueryTweak::NORMAL, elementsvv, element_filter4));
    EXPECT_EQ((std::vector<uint32_t>{ }), get_element_ids(QueryTweak::NORMAL, elementsvv, element_filter4));
    EXPECT_EQ((std::vector<std::vector<uint32_t>>{ { } }),
              extract_element_ids(QueryTweak::NORMAL, elementsvv, element_filter4));
}
