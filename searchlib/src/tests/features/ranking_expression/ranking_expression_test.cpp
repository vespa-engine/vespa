// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchlib/fef/featurenameparser.h>
#include <vespa/searchlib/features/rankingexpressionfeature.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>

using namespace search::features;
using namespace search::fef::test;
using namespace search::fef;
using namespace vespalib::eval;

using TypeMap = std::map<vespalib::string,vespalib::string>;

struct SetupResult {
    IndexEnvironment index_env;
    RankingExpressionBlueprint rank;
    DummyDependencyHandler deps;
    bool setup_ok;
    SetupResult(const TypeMap &object_inputs, const vespalib::string &expression);
    ~SetupResult();
};

SetupResult::SetupResult(const TypeMap &object_inputs,
                         const vespalib::string &expression)
    : index_env(), rank(), deps(rank), setup_ok(false)
{
    rank.setName("self");
    index_env.getProperties().add("self.rankingScript", expression);
    for (const auto &input: object_inputs) {
        deps.define_object_input(input.first, ValueType::from_spec(input.second));
    }
    setup_ok = rank.setup(index_env, {});
    EXPECT_TRUE(!deps.accept_type_mismatch);
}
SetupResult::~SetupResult() {}

void verify_output_type(const TypeMap &object_inputs,
                        const vespalib::string &expression, const FeatureType &expect)
{
    SetupResult result(object_inputs, expression);
    EXPECT_TRUE(result.setup_ok);
    EXPECT_EQUAL(1u, result.deps.output.size());
    ASSERT_EQUAL(1u, result.deps.output_type.size());
    if (expect.is_object()) {
        EXPECT_EQUAL(expect.type(), result.deps.output_type[0].type());
    } else {
        EXPECT_TRUE(!result.deps.output_type[0].is_object());
    }
}

void verify_setup_fail(const TypeMap &object_inputs,
                       const vespalib::string &expression)
{
    SetupResult result(object_inputs, expression);
    EXPECT_TRUE(!result.setup_ok);
    EXPECT_EQUAL(0u, result.deps.output.size());
}

TEST("require that expression with only number inputs produce number output (compiled)") {
    TEST_DO(verify_output_type({}, "a*b", FeatureType::number()));
}

TEST("require that expression with object input produces object output (interpreted)") {
    TEST_DO(verify_output_type({{"b", "double"}}, "a*b", FeatureType::object(ValueType::double_type())));
}

TEST("require that ranking expression can resolve to concrete complex type") {
    TEST_DO(verify_output_type({{"a", "tensor(x{},y{})"}, {"b", "tensor(y{},z{})"}}, "a*b",
                               FeatureType::object(ValueType::from_spec("tensor(x{},y{},z{})"))));
}

TEST("require that ranking expression can resolve to abstract complex type") {
    TEST_DO(verify_output_type({{"a", "tensor"}}, "a*b", FeatureType::object(ValueType::from_spec("tensor"))));
}

TEST("require that ranking expression can resolve to 'any' type") {
    TEST_DO(verify_output_type({{"a", "tensor(x{},y{})"}, {"b", "tensor"}}, "a*b",
                               FeatureType::object(ValueType::from_spec("any"))));
}

TEST("require that setup fails for incompatible types") {
    TEST_DO(verify_setup_fail({{"a", "tensor(x{},y{})"}, {"b", "tensor(y[10],z{})"}}, "a*b"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
