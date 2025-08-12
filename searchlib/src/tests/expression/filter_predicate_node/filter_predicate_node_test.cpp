// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/expression/and_predicate_node.h>
#include <vespa/searchlib/expression/constantnode.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <vespa/searchlib/expression/filter_predicate_node.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <vespa/searchlib/expression/not_predicate_node.h>
#include <vespa/searchlib/expression/or_predicate_node.h>
#include <vespa/searchlib/expression/range_predicate_node.h>
#include <vespa/searchlib/expression/regex_predicate_node.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::expression;

/**
 * Defines methods for declaratively creating filter expression trees.
 */
class FilterPredicateNodesTest : public ::testing::Test {
    std::unique_ptr<FilterPredicateNode> _node;

protected:
    FilterPredicateNodesTest();

    ~FilterPredicateNodesTest() override;

public:
    [[nodiscard]] bool evaluate() const;

    FilterPredicateNodesTest& set_node(std::unique_ptr<FilterPredicateNode> node);

    static std::unique_ptr<FilterPredicateNode> make_regex(const std::string& regex_value,
                                                           std::unique_ptr<ExpressionNode> result_node);

    static std::unique_ptr<FilterPredicateNode> make_range(double lower, double upper, bool lower_inclusive, bool upper_inclusive,
                                                           std::unique_ptr<ExpressionNode> result_node);

    static std::unique_ptr<FilterPredicateNode> make_not(std::unique_ptr<FilterPredicateNode> filter_node);

    template<typename... Nodes>
    static std::unique_ptr<FilterPredicateNode> make_or(Nodes... nodes);

    template<typename... Nodes>
    static std::unique_ptr<FilterPredicateNode> make_and(Nodes... nodes);

    static std::unique_ptr<ExpressionNode> make_result(const std::string& value);

    static std::unique_ptr<ExpressionNode> make_result(double value);
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

std::unique_ptr<FilterPredicateNode> FilterPredicateNodesTest::make_range(double lower, double upper, bool lower_inclusive, bool upper_inclusive,
                                                                          std::unique_ptr<ExpressionNode> result_node) {
    return std::make_unique<RangePredicateNode>(lower, upper, lower_inclusive, upper_inclusive, std::move(result_node));
}

std::unique_ptr<FilterPredicateNode> FilterPredicateNodesTest::make_not(std::unique_ptr<FilterPredicateNode> filter_node) {
    return std::make_unique<NotPredicateNode>(std::move(filter_node));
}

template<typename... Nodes>
std::unique_ptr<FilterPredicateNode> FilterPredicateNodesTest::make_or(Nodes... nodes) {
    auto or_predicate = std::make_unique<OrPredicateNode>();
    (or_predicate->args().emplace_back(std::move(nodes)), ...);
    return or_predicate;
}

template<typename... Nodes>
std::unique_ptr<FilterPredicateNode> FilterPredicateNodesTest::make_and(Nodes... nodes) {
    auto and_predicate = std::make_unique<AndPredicateNode>();
    (and_predicate->args().emplace_back(std::move(nodes)), ...);
    return and_predicate;
}

std::unique_ptr<ExpressionNode> FilterPredicateNodesTest::make_result(const std::string& value) {
    return std::make_unique<ConstantNode>(std::make_unique<StringResultNode>(value));
}

std::unique_ptr<ExpressionNode> FilterPredicateNodesTest::make_result(double value) {
    return std::make_unique<ConstantNode>(std::make_unique<FloatResultNode>(value));
}

TEST_F(FilterPredicateNodesTest, test_regex_match) {
    EXPECT_TRUE(set_node(make_regex("foo.*", make_result("foobar"))).evaluate());
    EXPECT_FALSE(set_node(make_regex("foo", make_result("foobar"))).evaluate());
    EXPECT_FALSE(set_node(make_regex("bar", make_result("foobar"))).evaluate());
}

TEST_F(FilterPredicateNodesTest, test_range_match_inside_range) {
    EXPECT_TRUE(set_node(make_range(0,100,false,false, make_result(6.0))).evaluate());
    EXPECT_TRUE(set_node(make_range(0,100,true,false, make_result(6.0))).evaluate());
    EXPECT_TRUE(set_node(make_range(0,100,false,true, make_result(6.0))).evaluate());
    EXPECT_TRUE(set_node(make_range(0,100,true,true, make_result(6.0))).evaluate());
}

TEST_F(FilterPredicateNodesTest, test_range_match_outside_range) {
    EXPECT_FALSE(set_node(make_range(50,100,false,false, make_result(6.0))).evaluate());
    EXPECT_FALSE(set_node(make_range(50,100,true,false, make_result(6.0))).evaluate());
    EXPECT_FALSE(set_node(make_range(50,100,false,true, make_result(6.0))).evaluate());
    EXPECT_FALSE(set_node(make_range(50,100,true,true, make_result(6.0))).evaluate());
    EXPECT_FALSE(set_node(make_range(50,100,false,false, make_result(101.0))).evaluate());
    EXPECT_FALSE(set_node(make_range(50,100,true,false, make_result(101.0))).evaluate());
    EXPECT_FALSE(set_node(make_range(50,100,false,true, make_result(101.0))).evaluate());
    EXPECT_FALSE(set_node(make_range(50,100,true,true, make_result(101.0))).evaluate());
}

TEST_F(FilterPredicateNodesTest, test_range_match_lower_bound) {
    EXPECT_TRUE(set_node(make_range(0,100,true,false, make_result(0.0))).evaluate());
    EXPECT_FALSE(set_node(make_range(0,100,false,false, make_result(0.0))).evaluate());
}

TEST_F(FilterPredicateNodesTest, test_range_match_upper_bound) {
    EXPECT_FALSE(set_node(make_range(0,100,true,false, make_result(100.0))).evaluate());
    EXPECT_TRUE(set_node(make_range(0,100,true,true, make_result(100.0))).evaluate());
}

TEST_F(FilterPredicateNodesTest, test_not_predicate) {
    EXPECT_FALSE(set_node(make_not(make_regex("foo.*", make_result("foobar")))).evaluate());
    EXPECT_TRUE(set_node(make_not(make_regex("foo", make_result("foobar")))).evaluate());
    EXPECT_TRUE(set_node(make_not(make_regex("bar", make_result("foobar")))).evaluate());
}

TEST_F(FilterPredicateNodesTest, test_or_no_match) {
    EXPECT_FALSE(
        set_node(make_or(
            make_regex("foo", make_result("foobar")),
            make_regex("bar", make_result("foobar")))).
        evaluate());
}

TEST_F(FilterPredicateNodesTest, test_or_one_match) {
    EXPECT_TRUE(
        set_node(make_or(
            make_regex("foo", make_result("foobar")),
            make_regex("foobar", make_result("foobar")))).
        evaluate());
    EXPECT_TRUE(
        set_node(make_or(
            make_regex("foobar", make_result("foobar")),
            make_regex("bar", make_result("foobar")))).
        evaluate());
}

TEST_F(FilterPredicateNodesTest, test_or_three_arguments) {
    EXPECT_TRUE(
        set_node(make_or(
            make_regex("foo", make_result("foobar")),
            make_regex("foobar", make_result("foobar")),
            make_regex("baz", make_result("foobar")))).
        evaluate());
    EXPECT_FALSE(
        set_node(make_or(
            make_regex("foo", make_result("foobar")),
            make_regex("bar", make_result("foobar")),
            make_regex("baz", make_result("foobar")))).
        evaluate());
}

TEST_F(FilterPredicateNodesTest, test_and_no_match) {
    EXPECT_FALSE(
        set_node(make_and(
            make_regex("foo", make_result("foobar")),
            make_regex("bar", make_result("foobar")))).
        evaluate());
}

TEST_F(FilterPredicateNodesTest, test_and_one_match) {
    EXPECT_FALSE(
        set_node(make_and(
            make_regex("foobar", make_result("foobar")),
            make_regex("bar", make_result("foobar")))).
        evaluate());
}

TEST_F(FilterPredicateNodesTest, test_and_all_match) {
    EXPECT_TRUE(
        set_node(make_and(
            make_regex("foo", make_result("foo")),
            make_regex("bar", make_result("bar")))).
        evaluate());
}

TEST_F(FilterPredicateNodesTest, test_and_three_arguments) {
    EXPECT_TRUE(
        set_node(make_and(
            make_regex("foo", make_result("foo")),
            make_regex("bar", make_result("bar")),
            make_regex("baz", make_result("baz")))).
        evaluate());
    EXPECT_FALSE(
        set_node(make_and(
            make_regex("foo", make_result("foo")),
            make_regex("bar", make_result("bar")),
            make_regex("baz", make_result("foobar")))).
        evaluate());
}

GTEST_MAIN_RUN_ALL_TESTS()
