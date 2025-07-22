// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/expression/constantnode.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <vespa/searchlib/expression/filter_predicate_node.h>
#include <vespa/searchlib/expression/regex_predicate_node.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::expression;

/**
 * Defines methods for declaratively create filter expression trees.
 */
class FilterPredicateNodesTest : public ::testing::Test {
    std::unique_ptr<FilterPredicateNode> _node;

protected:
    FilterPredicateNodesTest();

    ~FilterPredicateNodesTest() override;

public:
    bool evaluate() const;

    FilterPredicateNodesTest& set_node(std::unique_ptr<FilterPredicateNode> node);

    static std::unique_ptr<FilterPredicateNode> make_regex(const std::string& regex_value,
                                                       std::unique_ptr<ExpressionNode> result_node);

    static std::unique_ptr<ExpressionNode> make_result(const std::string& value);
};

FilterPredicateNodesTest::FilterPredicateNodesTest() = default;

FilterPredicateNodesTest::~FilterPredicateNodesTest() = default;

bool FilterPredicateNodesTest::evaluate() const {
    return _node->allow(42, 17.25);
}

FilterPredicateNodesTest& FilterPredicateNodesTest::set_node(std::unique_ptr<FilterPredicateNode> node) {
    _node = std::move(node);
    return *this;
}

std::unique_ptr<FilterPredicateNode> FilterPredicateNodesTest::make_regex(const std::string& regex_value,
                                                                      std::unique_ptr<ExpressionNode> result_node) {
    return std::make_unique<RegexPredicateNode>(regex_value, std::move(result_node));
}

std::unique_ptr<ExpressionNode> FilterPredicateNodesTest::make_result(const std::string& value) {
    return std::make_unique<ConstantNode>(
        ResultNode::UP(std::make_unique<StringResultNode>(value)->clone()));
}

TEST_F(FilterPredicateNodesTest, test_regex_match) {
    EXPECT_TRUE(set_node(make_regex("foo.*", make_result("foobar"))).evaluate());
    EXPECT_FALSE(set_node(make_regex("foo", make_result("foobar"))).evaluate());
    EXPECT_FALSE(set_node(make_regex("bar", make_result("foobar"))).evaluate());
}

GTEST_MAIN_RUN_ALL_TESTS()
