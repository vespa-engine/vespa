// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchlib/fef/featurenameparser.h>
#include <vespa/searchlib/features/rankingexpressionfeature.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <sstream>

using namespace search::features;
using namespace search::features::rankingexpression;
using namespace search::fef::test;
using namespace search::fef;
using namespace vespalib::eval;

using TypeMap = std::map<std::string,std::string>;

struct DummyExecutor : FeatureExecutor {
    void execute(uint32_t) override {}
};

struct DummyExpression : IntrinsicExpression {
    FeatureType type;
    DummyExpression(const FeatureType &type_in) : type(type_in) {}
    std::string describe_self() const override { return "dummy"; }
    const FeatureType &result_type() const override { return type; }
    void prepare_shared_state(const QueryEnv &, IObjectStore &) const override {
    }
    FeatureExecutor &create_executor(const QueryEnv &, vespalib::Stash &stash) const override {
        return stash.create<DummyExecutor>();
    }
};

struct DummyReplacer : ExpressionReplacer {
    std::string trigger;
    FeatureType type;
    DummyReplacer(const std::string trigger_in, const FeatureType &type_in)
        : trigger(trigger_in),
          type(type_in)
    {}
    IntrinsicExpression::UP maybe_replace(const vespalib::eval::Function &function,
                                          const search::fef::IIndexEnvironment &) const override
    {
        for (size_t i = 0; i < function.num_params(); ++i) {
            if (function.param_name(i) == trigger) {
                return std::make_unique<DummyExpression>(type);
            }
        }
        return IntrinsicExpression::UP(nullptr);
    }
};

ExpressionReplacer::SP make_replacer() {
    auto replacer = std::make_shared<ListExpressionReplacer>();
    replacer->add(std::make_unique<NullExpressionReplacer>());
    replacer->add(std::make_unique<DummyReplacer>("foo", FeatureType::number()));
    replacer->add(std::make_unique<DummyReplacer>("bar", FeatureType::object(ValueType::from_spec("tensor(x[5])"))));
    return replacer;
}

struct SetupResult {
    vespalib::Stash stash;
    IndexEnvironment index_env;
    QueryEnvironment query_env;
    RankingExpressionBlueprint rank;
    DummyDependencyHandler deps;
    bool setup_ok;
    SetupResult(const TypeMap &object_inputs, const std::string &expression,
                const std::string &expression_name = "");
    ~SetupResult();
};

SetupResult::SetupResult(const TypeMap &object_inputs,
                         const std::string &expression,
                         const std::string &expression_name)
    : stash(), index_env(), query_env(&index_env), rank(make_replacer()), deps(rank), setup_ok(false)
{
    rank.setName("self");
    for (const auto &input: object_inputs) {
        deps.define_object_input(input.first, ValueType::from_spec(input.second));
    }
    std::vector<std::string> params;
    if (expression_name.empty()) {
        index_env.getProperties().add("self.rankingScript", expression);
    } else {
        index_env.addRankingExpression(expression_name, expression);
        index_env.getProperties().add("self.expressionName", expression_name);
    }
    Blueprint &bp = rank;
    setup_ok = bp.setup(index_env, params);
    EXPECT_TRUE(!deps.accept_type_mismatch);
}
SetupResult::~SetupResult() = default;

void verify_output_type(const TypeMap &object_inputs,
                        const std::string &expression, const FeatureType &expect,
                        const std::string &expression_name = "")
{
    std::ostringstream os;
    os << "object_inputs=" << testing::PrintToString(object_inputs) << ", expression=" << std::quoted(expression);
    SCOPED_TRACE(os.str());
    SetupResult result(object_inputs, expression, expression_name);
    EXPECT_TRUE(result.setup_ok);
    EXPECT_EQ(1u, result.deps.output.size());
    ASSERT_EQ(1u, result.deps.output_type.size());
    if (expect.is_object()) {
        EXPECT_EQ(expect.type(), result.deps.output_type[0].type());
    } else {
        EXPECT_TRUE(!result.deps.output_type[0].is_object());
    }
}

void verify_setup_fail(const TypeMap &object_inputs,
                       const std::string &expression)
{
    SetupResult result(object_inputs, expression);
    EXPECT_TRUE(!result.setup_ok);
    EXPECT_EQ(0u, result.deps.output.size());
}

void verify_input_count(const std::string &expression, size_t expect) {
    SCOPED_TRACE(expression);
    SetupResult result({}, expression);
    EXPECT_TRUE(result.setup_ok);
    EXPECT_EQ(result.deps.input.size(), expect);
}

TEST(RankingExpressionTest, require_that_expression_with_only_number_inputs_produce_number_output_compiled) {
    verify_output_type({}, "a*b", FeatureType::number());
}

TEST(RankingExpressionTest, require_that_expression_with_object_input_produces_object_output_interpreted) {
    verify_output_type({{"b", "tensor(x{})"}}, "a*b", FeatureType::object(ValueType::from_spec("tensor(x{})")));
}

TEST(RankingExpressionTest, require_that_scalar_expressions_are_auto_unboxed_interpreted) {
    verify_output_type({{"b", "tensor(x{})"}}, "reduce(a*b,sum)", FeatureType::number());
}

TEST(RankingExpressionTest, require_that_ranking_expression_can_resolve_to_concrete_complex_type) {
    verify_output_type({{"a", "tensor(x{},y{})"}, {"b", "tensor(y{},z{})"}}, "a*b",
                       FeatureType::object(ValueType::from_spec("tensor(x{},y{},z{})")));
}

TEST(RankingExpressionTest, require_that_ranking_expression_can_be_external) {
    verify_output_type({}, "a*b", FeatureType::number(), "my_expr");
    verify_output_type({{"b", "double"}}, "a*b", FeatureType::number(), "my_expr");
    verify_output_type({{"a", "tensor(x{},y{})"}, {"b", "tensor(y{},z{})"}}, "a*b",
                       FeatureType::object(ValueType::from_spec("tensor(x{},y{},z{})")), "my_expr");
}

TEST(RankingExpressionTest, require_that_setup_fails_for_incompatible_types) {
    verify_setup_fail({{"a", "tensor(x{},y{})"}, {"b", "tensor(y[10],z{})"}}, "a*b");
}

TEST(RankingExpressionTest, require_that_replaced_expressions_have_no_inputs) {
    verify_input_count("a*b*c", 3u);
    verify_input_count("foo*b*c", 0u);
    verify_input_count("a*b*bar", 0u);
    verify_input_count("foo*b*bar", 0u);
}

TEST(RankingExpressionTest, require_that_replaced_expressions_override_result_type) {
    verify_output_type({{"b", "tensor(z{})"}}, "a*b*c",
                       FeatureType::object(ValueType::from_spec("tensor(z{})")));
    verify_output_type({{"b", "tensor(z{})"}}, "foo*b*c",
                       FeatureType::number());
    verify_output_type({{"b", "tensor(z{})"}}, "a*b*bar",
                       FeatureType::object(ValueType::from_spec("tensor(x[5])")));
    verify_output_type({{"b", "tensor(z{})"}}, "foo*b*bar",
                       FeatureType::number());
}

TEST(RankingExpressionTest, require_that_replaced_expressions_create_the_appropriate_executor) {
    SetupResult f1({}, "foo");
    EXPECT_TRUE(f1.setup_ok);
    FeatureExecutor &executor = f1.rank.createExecutor(f1.query_env, f1.stash);
    EXPECT_TRUE(dynamic_cast<DummyExecutor*>(&executor) != nullptr);
}

GTEST_MAIN_RUN_ALL_TESTS()
