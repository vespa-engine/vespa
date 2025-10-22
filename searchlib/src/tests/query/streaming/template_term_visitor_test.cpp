// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for streaming template_term_visitor.

#include <vespa/searchlib/query/streaming/template_term_visitor.h>
#include <vespa/searchlib/query/streaming/number_term.h>
#include <vespa/searchlib/query/streaming/string_term.h>
#include <vespa/searchlib/query/streaming/prefix_term.h>
#include <vespa/searchlib/query/streaming/and_query_node.h>
#include <vespa/searchlib/query/streaming/or_query_node.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchlib/query/query_normalization.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::QueryTermSimple;
using search::Normalizing;
using namespace search::streaming;

namespace {

class MyTermCollector : public TemplateTermVisitor<MyTermCollector>
{
public:
    int term_count = 0;

    template <class TermType>
    void visitTerm(TermType &) {
        term_count++;
    }
};

// Test that the visitor template compiles and can be instantiated
TEST(TemplateTermVisitorTest, visitor_can_be_instantiated) {
    MyTermCollector visitor;
    EXPECT_EQ(0, visitor.term_count);
}

// Test with simple term
TEST(TemplateTermVisitorTest, test_simple_term) {
    MyTermCollector visitor;

    auto term = std::make_unique<StringTerm>(
        std::make_unique<QueryNodeResultBase>(),
        "test", "field1", QueryTerm::Type::WORD, QueryTerm::Normalizing::LOWERCASE);

    term->accept(visitor);

    EXPECT_EQ(1, visitor.term_count);
}

// Test with connector node traversal
TEST(TemplateTermVisitorTest, test_connector_traversal) {
    MyTermCollector visitor;

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

    EXPECT_EQ(2, visitor.term_count);
}

// Test with nested connectors
TEST(TemplateTermVisitorTest, test_nested_connectors) {
    MyTermCollector visitor;

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

    EXPECT_EQ(3, visitor.term_count);
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
