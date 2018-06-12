// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/eval/eval/function.h>
#include <vespa/searchlib/features/max_reduce_prod_join_replacer.h>
#include <vespa/searchlib/features/rankingexpression/feature_name_extractor.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/blueprint.h>

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
    bool setup(const IIndexEnvironment &, const std::vector<vespalib::string> &params) override {
        EXPECT_EQUAL(getName(), "my_bp(foo,bar)");
        ASSERT_TRUE(params.size() == 2);
        EXPECT_EQUAL(params[0], "foo");
        EXPECT_EQUAL(params[1], "bar");
        describeOutput("out", "my output", FeatureType::number());
        was_used = true;
        return true;
    }
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &) const override {
        LOG_ABORT("should not be reached");
    }
};

bool replaced(const vespalib::string &expr) {
    bool was_used = false;
    ExpressionReplacer::UP replacer = MaxReduceProdJoinReplacer::create(std::make_unique<MyBlueprint>(was_used));
    Function rank_function = Function::parse(expr, FeatureNameExtractor());
    if (!EXPECT_TRUE(!rank_function.has_error())) {
        fprintf(stderr, "parse error: %s\n", rank_function.dump().c_str());
    }
    auto result = replacer->maybe_replace(rank_function, IndexEnvironment());    
    EXPECT_EQUAL(bool(result), was_used);
    return was_used;
}

TEST("require that matching expression with appropriate inputs is replaced") {
    EXPECT_TRUE(replaced("reduce(tensorFromLabels(attribute(foo),dim)*tensorFromWeightedSet(query(bar),dim),max)"));
}

TEST("require that matching expression with unrelated inputs is not replaced") {
    EXPECT_TRUE(!replaced("reduce(foo*bar,max)"));
}

TEST("require that input feature parameter lists have flexible matching") {
    EXPECT_TRUE(replaced("reduce(tensorFromLabels( attribute ( foo ) , dim )*tensorFromWeightedSet( query ( bar ) , dim ),max)"));
}

TEST("require that reduce dimension can be specified explicitly") {
    EXPECT_TRUE(replaced("reduce(tensorFromLabels(attribute(foo),dim)*tensorFromWeightedSet(query(bar),dim),max,dim)"));
}

TEST("require that expression using tensor join with lambda can also be replaced") {
    EXPECT_TRUE(replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(x*y)),max)"));
}

TEST("require that parameter ordering does not matter") {
    EXPECT_TRUE(replaced("reduce(tensorFromWeightedSet(query(bar),dim)*tensorFromLabels(attribute(foo),dim),max)"));
    EXPECT_TRUE(replaced("reduce(join(tensorFromWeightedSet(query(bar),dim),tensorFromLabels(attribute(foo),dim),f(x,y)(x*y)),max)"));
    EXPECT_TRUE(replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(y*x)),max)"));
}

TEST("require that source specifiers must match") {
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(query(foo),dim)*tensorFromWeightedSet(attribute(bar),dim),max)"));
}

TEST("require that reduce operation must match") {
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),dim)*tensorFromWeightedSet(query(bar),dim),min)"));
}

TEST("require that join operation must match") {
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),dim)+tensorFromWeightedSet(query(bar),dim),max)"));
    EXPECT_TRUE(!replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(x+y)),max)"));
    EXPECT_TRUE(!replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(x*x)),max)"));
    EXPECT_TRUE(!replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(y*y)),max)"));
    EXPECT_TRUE(!replaced("reduce(join(tensorFromLabels(attribute(foo),dim),tensorFromWeightedSet(query(bar),dim),f(x,y)(x*y*1)),max)"));
}

TEST("require that reduce dimension must match") {
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),x)*tensorFromWeightedSet(query(bar),x),max,y)"));
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),x)*tensorFromWeightedSet(query(bar),y),max)"));
    EXPECT_TRUE(!replaced("reduce(tensorFromLabels(attribute(foo),x)*tensorFromWeightedSet(query(bar),x),max,x,y)"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
