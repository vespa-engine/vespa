// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/features/rankingexpressionfeature.h>

using namespace search;
using namespace search::fef;
using search::features::RankingExpressionBlueprint;

class BaseBlueprint : public Blueprint {
public:
    BaseBlueprint() : Blueprint("base") { }
    virtual void visitDumpFeatures(const IIndexEnvironment &,
                                   IDumpFeatureVisitor &) const override {}
    virtual Blueprint::UP createInstance() const override { return Blueprint::UP(new BaseBlueprint()); }
    virtual bool setup(const IIndexEnvironment & indexEnv,
                       const ParameterList & params) override {
        (void) indexEnv; (void) params;
        describeOutput("foo", "foo");
        describeOutput("bar", "bar");
        describeOutput("baz", "baz");
        return true;
    }
    virtual FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        std::vector<feature_t> values;
        values.push_back(0.0);
        values.push_back(0.0);
        values.push_back(0.0);
        return stash.create<features::ValueExecutor>(values);
    }
};

class CombineBlueprint : public Blueprint {
public:
    CombineBlueprint() : Blueprint("combine") { }
    virtual void visitDumpFeatures(const IIndexEnvironment &,
                                   IDumpFeatureVisitor &) const override {}
    virtual Blueprint::UP createInstance() const override { return Blueprint::UP(new CombineBlueprint()); }
    virtual bool setup(const IIndexEnvironment & indexEnv,
                       const ParameterList & params) override {
        (void) indexEnv; (void) params;
        ASSERT_TRUE(defineInput("base.foo"));
        ASSERT_TRUE(defineInput("base.bar"));
        ASSERT_TRUE(defineInput("base.baz"));
        describeOutput("out", "out");
        return true;
    }
    virtual FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        return stash.create<features::SingleZeroValueExecutor>();
    }
};

struct Fixture {
    BlueprintFactory factory;    
    Fixture() {
        factory.addPrototype(Blueprint::SP(new BaseBlueprint()));
        factory.addPrototype(Blueprint::SP(new CombineBlueprint()));
        factory.addPrototype(std::make_shared<RankingExpressionBlueprint>());
    }
};

TEST_F("requireThatWeGetUniqueBlueprints", Fixture()) {
    test::IndexEnvironment ienv;
    BlueprintResolver::SP res(new BlueprintResolver(f.factory, ienv));
    res->addSeed("combine");
    EXPECT_TRUE(res->compile());
    const BlueprintResolver::ExecutorSpecList & spec = res->getExecutorSpecs();
    ASSERT_EQUAL(2u, spec.size());
    EXPECT_TRUE(dynamic_cast<BaseBlueprint *>(spec[0].blueprint.get()) != NULL);
    EXPECT_TRUE(dynamic_cast<CombineBlueprint *>(spec[1].blueprint.get()) != NULL);
}

TEST_F("require_that_bad_input_is_handled", Fixture) {
    test::IndexEnvironment ienv;
    ienv.getProperties().add(indexproperties::eval::LazyExpressions::NAME, "false");
    ienv.getProperties().add("rankingExpression(badinput).rankingScript", "base.foobad + base.bar");
    BlueprintResolver::SP res(new BlueprintResolver(f.factory, ienv));
    res->addSeed("rankingExpression(badinput)");
    EXPECT_FALSE(res->compile());
    const BlueprintResolver::ExecutorSpecList & spec = res->getExecutorSpecs();
    ASSERT_EQUAL(2u, spec.size());
    EXPECT_TRUE(dynamic_cast<BaseBlueprint *>(spec[0].blueprint.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<RankingExpressionBlueprint *>(spec[1].blueprint.get()) != nullptr);
}

TEST("require that features can be described") {
    EXPECT_EQUAL(BlueprintResolver::describe_feature("featureName"), vespalib::string("rank feature featureName"));
    EXPECT_EQUAL(BlueprintResolver::describe_feature("rankingExpression(foo)"), vespalib::string("function foo"));
    EXPECT_EQUAL(BlueprintResolver::describe_feature("rankingExpression(foo@1234.5678)"), vespalib::string("function foo"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
