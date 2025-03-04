// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/searchlib/features/max_reduce_prod_join_replacer.h>
#include <vespa/searchlib/features/rankingexpression/feature_name_extractor.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("max_reduce_prod_join_replacer_test");

using search::features::MaxReduceProdJoinReplacer;
using search::features::rankingexpression::ExpressionReplacer;
using search::features::rankingexpression::FeatureNameExtractor;
using search::fef::Blueprint;
using search::fef::FeatureExecutor;
using search::fef::FeatureType;
using search::fef::IDumpFeatureVisitor;
using search::fef::IIndexEnvironment;
using search::fef::IQueryEnvironment;
using search::fef::test::IndexEnvironment;
using vespalib::Stash;
using vespalib::eval::Function;

struct MyBlueprint : Blueprint {
    bool &was_used;
    MyBlueprint(bool &was_used_out) : Blueprint("my_bp"), was_used(was_used_out) {}
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return std::make_unique<MyBlueprint>(was_used); }
    bool setup(const IIndexEnvironment &, const std::vector<std::string> &params) override {
        EXPECT_EQ(getName(), "my_bp(foo,bar)");
        EXPECT_EQ(2, params.size());
        if (params.size() != 2) {
            return false;
        }
        EXPECT_EQ(params[0], "foo");
        EXPECT_EQ(params[1], "bar");
        describeOutput("out", "my output", FeatureType::number());
        was_used = true;
        return true;
    }
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &) const override {
        LOG_ABORT("should not be reached");
    }
};

bool replaced(const std::string &expr) {
    bool was_used = false;
    ExpressionReplacer::UP replacer = MaxReduceProdJoinReplacer::create(std::make_unique<MyBlueprint>(was_used));
    auto rank_function = Function::parse(expr, FeatureNameExtractor());
    EXPECT_TRUE(!rank_function->has_error()) << "parse error: " << rank_function->dump();
    auto result = replacer->maybe_replace(*rank_function, IndexEnvironment());
    EXPECT_EQ(bool(result), was_used);
    return was_used;
}

TEST(MaxReduceProdJoinReplacerTest, require_that_matching_expression_with_appropriate_inputs_is_replaced) {
    EXPECT_TRUE(replaced("reduce(tensorFromLabels(attribute(foo),dim)*tensorFromWeightedSet(query(bar),dim),max)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_matching_expression_with_unrelated_inputs_is_not_replaced) {
    EXPECT_TRUE(!replaced("reduce(foo*bar,max)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_input_feature_parameter_lists_have_flexible_matching) {
    EXPECT_TRUE(replaced("reduce(tensorFromLabels( attribute ( foo ) , dim )*tensorFromWeightedSet( query ( bar ) , dim ),max)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_reduce_dimension_can_be_specified_explicitly) {
    EXPECT_TRUE(replaced("reduce(tensorFromLabels(attribute(foo),dim)*tensorFromWeightedSet(query(bar),dim),max,dim)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_expression_using_tensor_join_with_lambda_can_also_be_replaced) {
    EXPECT_TRUE(replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(x*y)),max)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_parameter_ordering_does_not_matter) {
    EXPECT_TRUE(replaced("reduce(tensorFromWeightedSet(query(bar),dim)*tensorFromLabels(attribute(foo),dim),max)"));
    EXPECT_TRUE(replaced("reduce(join(tensorFromWeightedSet(query(bar),dim),tensorFromLabels(attribute(foo),dim),f(x,y)(x*y)),max)"));
    EXPECT_TRUE(replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(y*x)),max)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_source_specifiers_must_match) {
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(query(foo),dim)*tensorFromWeightedSet(attribute(bar),dim),max)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_reduce_operation_must_match) {
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),dim)*tensorFromWeightedSet(query(bar),dim),min)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_join_operation_must_match) {
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),dim)+tensorFromWeightedSet(query(bar),dim),max)"));
    EXPECT_TRUE(!replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(x+y)),max)"));
    EXPECT_TRUE(!replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(x*x)),max)"));
    EXPECT_TRUE(!replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(y*y)),max)"));
    EXPECT_TRUE(!replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(x*y*1)),max)"));
}

TEST(MaxReduceProdJoinReplacerTest, require_that_reduce_dimension_must_match) {
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),x)*tensorFromWeightedSet(query(bar),x),max,y)"));
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),x)*tensorFromWeightedSet(query(bar),y),max)"));
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),x)*tensorFromWeightedSet(query(bar),x),max,x,y)"));
}

GTEST_MAIN_RUN_ALL_TESTS()
