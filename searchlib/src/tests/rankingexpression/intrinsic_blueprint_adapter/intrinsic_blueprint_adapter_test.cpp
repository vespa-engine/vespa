// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/features/rankingexpression/intrinsic_blueprint_adapter.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/vespalib/util/stash.h>
#include <set>

using namespace search::features::rankingexpression;
using namespace search::fef::test;
using namespace search::fef;
using vespalib::Stash;
using vespalib::eval::ValueType;

std::string fail_setup    = "fail_setup";
std::string extra_input   = "extra_input";
std::string extra_output  = "extra_output";
std::string no_output     = "no_output";
std::string object_result = "object_result";
std::string error_result  = "error_result";

struct MyExecutor : FeatureExecutor {
    void execute(uint32_t) override {}
};

struct MyBlueprint : Blueprint {
    std::set<std::string> flags;
    MyBlueprint() : Blueprint("my_bp"), flags() {}
    MyBlueprint(const std::set<std::string> &flags_in) : Blueprint("my_bp"), flags(flags_in) {}
    void set(const std::string &flag) { flags.insert(flag); }
    bool is_set(const std::string &flag) const { return (flags.count(flag) > 0); }
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return std::make_unique<MyBlueprint>(flags); }
    bool setup(const IIndexEnvironment &, const std::vector<std::string> &params) override {
        EXPECT_EQ(getName(), "my_bp(foo,bar)");        
        assert(params.size() == 2);
        EXPECT_EQ(params[0], "foo");
        EXPECT_EQ(params[1], "bar");
        if (is_set(extra_input)) {
            EXPECT_TRUE(!defineInput("my_input", AcceptInput::ANY).has_value());
        }
        if (!is_set(no_output)) {
            if (is_set(error_result)) {
                describeOutput("out", "my output", FeatureType::object(ValueType::error_type()));
            } else {
                if (is_set(object_result)) {
                    describeOutput("out", "my output", FeatureType::object(ValueType::double_type()));
                } else {
                    describeOutput("out", "my output", FeatureType::number());
                }
            }
            if (is_set(extra_output)) {
                describeOutput("extra", "my extra output", FeatureType::number());
            }
        }
        return !is_set(fail_setup);
    }
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        return stash.create<MyExecutor>();
    }
};

struct IntrinsicBlueprintAdapterTest : public ::testing::Test {
    Stash stash;
    IndexEnvironment idx_env;
    QueryEnvironment query_env;
    MyBlueprint blueprint;
    IntrinsicBlueprintAdapterTest() : stash(), idx_env(), query_env(&idx_env), blueprint() {}
    IntrinsicExpression::UP create() const {
        return IntrinsicBlueprintAdapter::try_create(blueprint, idx_env, {"foo", "bar"});
    }
};

TEST_F(IntrinsicBlueprintAdapterTest, require_that_blueprints_can_be_used_for_intrinsic_expressions) {
    auto expression = create();
    ASSERT_TRUE(bool(expression));
    EXPECT_TRUE(!expression->result_type().is_object());
    auto &executor = expression->create_executor(query_env, stash);
    EXPECT_TRUE(dynamic_cast<MyExecutor*>(&executor) != nullptr);
}

TEST_F(IntrinsicBlueprintAdapterTest, require_that_result_type_is_propagated_for_intrinsic_blueprints) {
    blueprint.set(object_result);
    auto expression = create();
    ASSERT_TRUE(bool(expression));
    EXPECT_TRUE(expression->result_type().is_object());
    EXPECT_TRUE(expression->result_type().type().is_double());
}

TEST_F(IntrinsicBlueprintAdapterTest, require_that_intrinsic_blueprint_adaption_fails_if_blueprint_setup_fails) {
    blueprint.set(fail_setup);
    EXPECT_TRUE(create().get() == nullptr);
}

TEST_F(IntrinsicBlueprintAdapterTest, require_that_intrinsic_blueprint_adaption_fails_if_blueprint_has_inputs) {
    blueprint.set(extra_input);
    EXPECT_TRUE(create().get() == nullptr);
}

TEST_F(IntrinsicBlueprintAdapterTest, require_that_intrinsic_blueprint_adaption_fails_if_blueprint_has_more_than_one_output) {
    blueprint.set(extra_output);
    EXPECT_TRUE(create().get() == nullptr);
}

TEST_F(IntrinsicBlueprintAdapterTest, require_that_intrinsic_blueprint_adaption_fails_if_blueprint_has_no_result) {
    blueprint.set(no_output);
    EXPECT_TRUE(create().get() == nullptr);
}

TEST_F(IntrinsicBlueprintAdapterTest, require_that_intrinsic_blueprint_adaption_fails_if_blueprint_has_error_typed_output) {
    blueprint.set(error_result);
    EXPECT_TRUE(create().get() == nullptr);
}

GTEST_MAIN_RUN_ALL_TESTS()
