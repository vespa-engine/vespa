// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/check_require.h>
#include <vespa/searchlib/features/rankingexpressionfeature.h>
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search;
using namespace search::fef;
using search::features::RankingExpressionBlueprint;

class BaseBlueprint : public Blueprint {
public:
    BaseBlueprint() : Blueprint("base") {}
    ~BaseBlueprint() override;
    void visitDumpFeatures(const IIndexEnvironment&, IDumpFeatureVisitor&) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new BaseBlueprint()); }
    bool setup(const IIndexEnvironment& indexEnv, const ParameterList& params) override {
        (void)indexEnv;
        (void)params;
        describeOutput("foo", "foo");
        describeOutput("bar", "bar");
        describeOutput("baz", "baz");
        return true;
    }
    FeatureExecutor& createExecutor(const IQueryEnvironment&, vespalib::Stash& stash) const override {
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
        CHECK(type.has_value());
    }

public:
    CombineBlueprint() : Blueprint("combine") {}

    ~CombineBlueprint() override;

    void visitDumpFeatures(const IIndexEnvironment&, IDumpFeatureVisitor&) const override {}

    Blueprint::UP createInstance() const override { return Blueprint::UP(new CombineBlueprint()); }

    bool setup(const IIndexEnvironment& indexEnv, const ParameterList& params) override {
        (void)indexEnv;
        (void)params;
        assert_define_input("base.foo");
        assert_define_input("base.bar");
        assert_define_input("base.baz");
        describeOutput("out", "out");
        return true;
    }
    FeatureExecutor& createExecutor(const IQueryEnvironment&, vespalib::Stash& stash) const override {
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
    ~ResolverTest() override;
};

ResolverTest::~ResolverTest() = default;

TEST_F(ResolverTest, requireThatWeGetUniqueBlueprints) {
    test::IndexEnvironment ienv;
    BlueprintResolver::SP  res(new BlueprintResolver(factory, ienv));
    res->addSeed("combine");
    EXPECT_TRUE(res->compile());
    const BlueprintResolver::ExecutorSpecList& spec = res->getExecutorSpecs();
    ASSERT_EQ(2u, spec.size());
    EXPECT_TRUE(dynamic_cast<BaseBlueprint*>(spec[0].blueprint.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<CombineBlueprint*>(spec[1].blueprint.get()) != nullptr);
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
    EXPECT_TRUE(dynamic_cast<BaseBlueprint*>(spec[0].blueprint.get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<RankingExpressionBlueprint*>(spec[1].blueprint.get()) != nullptr);
}

using Ref = std::pair<uint32_t, uint32_t>;

Ref ref(const BlueprintResolver::FeatureRef& feature_ref) {
    return {feature_ref.executor, feature_ref.output};
}

// Specs: 0 rankingExpression(y), 1 base, 2 combine, 3 rankingExpression(x).
// Both combine and x depend on base; y depends on nothing.
struct SelectSeedsTest : ResolverTest {
    test::IndexEnvironment ienv;
    BlueprintResolver      res;
    SelectSeedsTest() : ResolverTest(), ienv(), res(factory, ienv) {
        ienv.getProperties().add("rankingExpression(x).rankingScript", "base.baz");
        ienv.getProperties().add("rankingExpression(y).rankingScript", "2");
        res.addSeed("rankingExpression(y)");
        res.addSeed("combine");
        res.addSeed("rankingExpression(x)");
    }
    ~SelectSeedsTest() override;
    void SetUp() override {
        ASSERT_TRUE(res.compile());
        ASSERT_EQ(4u, res.getExecutorSpecs().size());
    }
};

SelectSeedsTest::~SelectSeedsTest() = default;

TEST_F(SelectSeedsTest, selected_seeds_keep_only_their_dependencies) {
    auto selected = res.select_seeds({"rankingExpression(x)", "combine"});
    ASSERT_TRUE(selected);
    const auto& specs = selected->getExecutorSpecs();
    ASSERT_EQ(3u, specs.size());
    EXPECT_EQ(res.getExecutorSpecs()[1].blueprint, specs[0].blueprint);
    EXPECT_EQ(res.getExecutorSpecs()[2].blueprint, specs[1].blueprint);
    EXPECT_EQ(res.getExecutorSpecs()[3].blueprint, specs[2].blueprint);
    ASSERT_EQ(3u, specs[1].inputs.size());
    EXPECT_EQ(Ref(0, 0), ref(specs[1].inputs[0]));
    EXPECT_EQ(Ref(0, 1), ref(specs[1].inputs[1]));
    EXPECT_EQ(Ref(0, 2), ref(specs[1].inputs[2]));
    ASSERT_EQ(1u, specs[2].inputs.size());
    EXPECT_EQ(Ref(0, 2), ref(specs[2].inputs[0]));

    const auto& seeds = selected->getSeedMap();
    ASSERT_EQ(2u, seeds.size());
    EXPECT_EQ(Ref(1, 0), ref(seeds.at("combine")));
    EXPECT_EQ(Ref(2, 0), ref(seeds.at("rankingExpression(x)")));

    const auto& features = selected->getFeatureMap();
    EXPECT_EQ(Ref(0, 0), ref(features.at("base")));
    EXPECT_EQ(Ref(0, 2), ref(features.at("base.baz")));
    EXPECT_EQ(Ref(1, 0), ref(features.at("combine.out")));
    EXPECT_EQ(Ref(2, 0), ref(features.at("rankingExpression(x)")));
    for (const auto& [name, feature_ref] : features) {
        EXPECT_EQ(std::string::npos, name.find("rankingExpression(y)")) << name;
        EXPECT_LT(feature_ref.executor, specs.size()) << name;
    }
}

TEST_F(SelectSeedsTest, selecting_leaves_the_source_and_earlier_selections_unchanged) {
    auto first = res.select_seeds({"rankingExpression(x)"});
    ASSERT_TRUE(first);
    auto second = res.select_seeds({"rankingExpression(y)"});
    ASSERT_TRUE(second);
    EXPECT_EQ(1u, second->getExecutorSpecs().size());

    ASSERT_EQ(2u, first->getExecutorSpecs().size());
    EXPECT_EQ(Ref(0, 2), ref(first->getExecutorSpecs()[1].inputs[0]));
    EXPECT_EQ(1u, first->getSeedMap().size());

    ASSERT_EQ(4u, res.getExecutorSpecs().size());
    EXPECT_EQ(Ref(1, 2), ref(res.getExecutorSpecs()[3].inputs[0]));
    EXPECT_EQ(3u, res.getSeedMap().size());
    EXPECT_EQ(Ref(0, 0), ref(res.getFeatureMap().at("rankingExpression(y)")));
}

TEST_F(SelectSeedsTest, only_seeds_can_be_selected) {
    EXPECT_FALSE(res.select_seeds({"base"}));
    EXPECT_FALSE(res.select_seeds({"rankingExpression(x)", "unknown"}));
    auto none = res.select_seeds({});
    ASSERT_TRUE(none);
    EXPECT_TRUE(none->getExecutorSpecs().empty());
}

TEST_F(ResolverTest, require_that_features_can_be_described) {
    EXPECT_EQ(BlueprintResolver::describe_feature("featureName"), std::string("rank feature featureName"));
    EXPECT_EQ(BlueprintResolver::describe_feature("rankingExpression(foo)"), std::string("function foo"));
    EXPECT_EQ(BlueprintResolver::describe_feature("rankingExpression(foo@1234.5678)"), std::string("function foo"));
}

GTEST_MAIN_RUN_ALL_TESTS()
