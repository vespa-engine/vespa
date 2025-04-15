// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
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
    ~BaseBlueprint() override;
    void visitDumpFeatures(const IIndexEnvironment &,
                           IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new BaseBlueprint()); }
    bool setup(const IIndexEnvironment & indexEnv,
               const ParameterList & params) override {
        (void) indexEnv; (void) params;
        describeOutput("foo", "foo");
        describeOutput("bar", "bar");
        describeOutput("baz", "baz");
        return true;
    }
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        std::vector<feature_t> values;
        values.push_back(0.0);
        values.push_back(0.0);
        values.push_back(0.0);
        return stash.create<features::ValueExecutor>(values);
    }
};

BaseBlueprint::~BaseBlueprint() = default;

class CombineBlueprint : public Blueprint {
private:
    void assert_define_input(const std::string& in_name) {
        auto type = defineInput(in_name);
        assert(type.has_value());
    }

public:
    CombineBlueprint() : Blueprint("combine") { }

    ~CombineBlueprint() override;

    void visitDumpFeatures(const IIndexEnvironment &,
                           IDumpFeatureVisitor &) const override {}

    Blueprint::UP createInstance() const override { return Blueprint::UP(new CombineBlueprint()); }

    bool setup(const IIndexEnvironment& indexEnv,
               const ParameterList& params) override {
        (void) indexEnv;
        (void) params;
        assert_define_input("base.foo");
        assert_define_input("base.bar");
        assert_define_input("base.baz");
        describeOutput("out", "out");
        return true;
    }
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        return stash.create<features::SingleZeroValueExecutor>();
    }
};

CombineBlueprint::~CombineBlueprint() = default;

struct ResolverTest : public ::testing::Test {
    BlueprintFactory factory;

    ResolverTest() {
        factory.addPrototype(Blueprint::SP(new BaseBlueprint()));
        factory.addPrototype(Blueprint::SP(new CombineBlueprint()));
        factory.addPrototype(std::make_shared<RankingExpressionBlueprint>());
    }
};

TEST_F(ResolverTest, requireThatWeGetUniqueBlueprints) {
    test::IndexEnvironment ienv;
    BlueprintResolver::SP res(new BlueprintResolver(factory, ienv));
    res->addSeed("combine");
    EXPECT_TRUE(res->compile());
    const BlueprintResolver::ExecutorSpecList& spec = res->getExecutorSpecs();
    ASSERT_EQ(2u, spec.size());
    EXPECT_TRUE(dynamic_cast<BaseBlueprint *>(spec[0].blueprint.get()) != NULL);
    EXPECT_TRUE(dynamic_cast<CombineBlueprint *>(spec[1].blueprint.get()) != NULL);
}

TEST_F(ResolverTest, require_that_bad_input_is_handled) {
    test::IndexEnvironment ienv;
    ienv.getProperties().add(indexproperties::eval::LazyExpressions::NAME, "false");
    ienv.getProperties().add("rankingExpression(badinput).rankingScript", "base.foobad + base.bar");
    BlueprintResolver::SP res(new BlueprintResolver(factory, ienv));
    res->addSeed("rankingExpression(badinput)");
    EXPECT_FALSE(res->compile());
    const BlueprintResolver::ExecutorSpecList& spec = res->getExecutorSpecs();
    ASSERT_EQ(2u, spec.size());
    EXPECT_TRUE(dynamic_cast<BaseBlueprint *>(spec[0].blueprint.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<RankingExpressionBlueprint *>(spec[1].blueprint.get()) != nullptr);
}

TEST_F(ResolverTest, require_that_features_can_be_described) {
    EXPECT_EQ(BlueprintResolver::describe_feature("featureName"), std::string("rank feature featureName"));
    EXPECT_EQ(BlueprintResolver::describe_feature("rankingExpression(foo)"), std::string("function foo"));
    EXPECT_EQ(BlueprintResolver::describe_feature("rankingExpression(foo@1234.5678)"), std::string("function foo"));
}

GTEST_MAIN_RUN_ALL_TESTS()
