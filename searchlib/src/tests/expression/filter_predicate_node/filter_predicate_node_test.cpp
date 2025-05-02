// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/expression/constantnode.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <vespa/searchlib/expression/filter_predicate_node.h>
#include <vespa/searchlib/expression/regex_predicate_node.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::expression;

struct Fixture {
    std::unique_ptr<StringResultNode> _input;
    std::unique_ptr<FilterPredicateNode> _node;

    Fixture();
    ~Fixture();

    Fixture& setup_doc(const char * field_value);
    // Fixture& setup_doc(std::vector<std::string> field_value);
    Fixture& setup_node(std::string regex_value);
    bool evaluate();
};

Fixture::Fixture() : _input(), _node() {}
Fixture::~Fixture() = default;


Fixture&
Fixture::setup_doc(const char * field_value)
{
    _input = std::make_unique<StringResultNode>(field_value);
    return *this;
}

Fixture&
Fixture::setup_node(std::string regex_value)
{
    _node = std::make_unique<RegexPredicateNode>(regex_value,
                                                 std::make_unique<ConstantNode>(
                                                         ResultNode::UP(_input->clone())));
    return *this;
}

bool
Fixture::evaluate()
{
    return _node->allow(42, 17.25);
}


class RegexFilterTest : public Fixture, public ::testing::Test
{
protected:
    RegexFilterTest();
    ~RegexFilterTest() override;
};

RegexFilterTest::RegexFilterTest() = default;
RegexFilterTest::~RegexFilterTest() = default;

TEST_F(RegexFilterTest, test_regex_match)
{
    EXPECT_TRUE(setup_doc("foobar").setup_node("foo.*").evaluate());
    EXPECT_FALSE(setup_doc("foobar").setup_node("foo").evaluate());
    EXPECT_FALSE(setup_doc("foobar").setup_node("bar").evaluate());
}

GTEST_MAIN_RUN_ALL_TESTS()
