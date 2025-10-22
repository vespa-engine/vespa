// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/streaming/term_visitor.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/streaming/string_term.h>
#include <vespa/searchlib/query/streaming/prefix_term.h>
#include <vespa/searchlib/query/streaming/and_query_node.h>
#include <vespa/searchlib/query/streaming/or_query_node.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::streaming;

namespace {

class SimpleTermCounter : public TermVisitor {
public:
    int count = 0;

    void countTerm(QueryTerm &term) {
        (void)term;
        count++;
    }

protected:
    void visit(FuzzyTerm &n) override { countTerm(n); }
    void visit(InTerm &n) override { countTerm(n); }
    void visit(LocationTerm &n) override { countTerm(n); }
    void visit(NearestNeighborQueryNode &n) override { countTerm(n); }
    void visit(NumberTerm &n) override { countTerm(n); }
    void visit(PredicateQuery &n) override { countTerm(n); }
    void visit(PrefixTerm &n) override { countTerm(n); }
    void visit(QueryTerm &n) override { countTerm(n); }
    void visit(RangeTerm &n) override { countTerm(n); }
    void visit(RegexpTerm &n) override { countTerm(n); }
    void visit(StringTerm &n) override { countTerm(n); }
    void visit(SubstringTerm &n) override { countTerm(n); }
    void visit(SuffixTerm &n) override { countTerm(n); }
    void visit(DotProductTerm &n) override { countTerm(n); }
    void visit(WandTerm &n) override { countTerm(n); }
    void visit(WeightedSetTerm &n) override { countTerm(n); }
    void visit(WordAlternatives &n) override { countTerm(n); }
};

class TermIndexCollector : public TermVisitor {
public:
    std::vector<std::string> indexes;

    void collectIndex(QueryTerm &term) {
        indexes.push_back(term.getIndex());
    }

protected:
    void visit(FuzzyTerm &n) override { collectIndex(n); }
    void visit(InTerm &n) override { collectIndex(n); }
    void visit(LocationTerm &n) override { collectIndex(n); }
    void visit(NearestNeighborQueryNode &n) override { collectIndex(n); }
    void visit(NumberTerm &n) override { collectIndex(n); }
    void visit(PredicateQuery &n) override { collectIndex(n); }
    void visit(PrefixTerm &n) override { collectIndex(n); }
    void visit(QueryTerm &n) override { collectIndex(n); }
    void visit(RangeTerm &n) override { collectIndex(n); }
    void visit(RegexpTerm &n) override { collectIndex(n); }
    void visit(StringTerm &n) override { collectIndex(n); }
    void visit(SubstringTerm &n) override { collectIndex(n); }
    void visit(SuffixTerm &n) override { collectIndex(n); }
    void visit(DotProductTerm &n) override { collectIndex(n); }
    void visit(WandTerm &n) override { collectIndex(n); }
    void visit(WeightedSetTerm &n) override { collectIndex(n); }
    void visit(WordAlternatives &n) override { collectIndex(n); }
};

}

TEST(TermVisitorTest, test_simple_term_counting) {
    SimpleTermCounter visitor;

    // Create a simple term
    auto term = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test", "field1", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    term->accept(visitor);

    EXPECT_EQ(1, visitor.count);
}

TEST(TermVisitorTest, test_connector_traversal) {
    SimpleTermCounter visitor;

    // Create an AND node with two terms
    auto and_node = std::make_unique<AndQueryNode>();

    auto term1 = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test1", "field1", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    auto term2 = std::make_unique<PrefixTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test2", "field2", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    and_node->addChild(std::move(term1));
    and_node->addChild(std::move(term2));

    and_node->accept(visitor);

    EXPECT_EQ(2, visitor.count);
}

TEST(TermVisitorTest, test_nested_connectors) {
    SimpleTermCounter visitor;

    // Create nested structure: AND(OR(term1, term2), term3)
    auto and_node = std::make_unique<AndQueryNode>();
    auto or_node = std::make_unique<OrQueryNode>();

    auto term1 = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test1", "field1", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    auto term2 = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test2", "field2", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    auto term3 = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test3", "field3", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    or_node->addChild(std::move(term1));
    or_node->addChild(std::move(term2));

    and_node->addChild(std::move(or_node));
    and_node->addChild(std::move(term3));

    and_node->accept(visitor);

    EXPECT_EQ(3, visitor.count);
}

TEST(TermVisitorTest, test_index_collection) {
    TermIndexCollector visitor;

    auto and_node = std::make_unique<AndQueryNode>();

    auto term1 = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test1", "field1", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    auto term2 = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test2", "field2", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    auto term3 = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test3", "field1", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    and_node->addChild(std::move(term1));
    and_node->addChild(std::move(term2));
    and_node->addChild(std::move(term3));

    and_node->accept(visitor);

    ASSERT_EQ(3u, visitor.indexes.size());
    EXPECT_EQ("field1", visitor.indexes[0]);
    EXPECT_EQ("field2", visitor.indexes[1]);
    EXPECT_EQ("field1", visitor.indexes[2]);
}

GTEST_MAIN_RUN_ALL_TESTS()
