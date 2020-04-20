// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

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

vespalib::string fail_setup    = "fail_setup";
vespalib::string extra_input   = "extra_input";
vespalib::string extra_output  = "extra_output";
vespalib::string no_output     = "no_output";
vespalib::string object_result = "object_result";
vespalib::string error_result  = "error_result";

struct MyExecutor : FeatureExecutor {
    void execute(uint32_t) override {}
};

struct MyBlueprint : Blueprint {
    std::set<vespalib::string> flags;
    MyBlueprint() : Blueprint("my_bp"), flags() {}
    MyBlueprint(const std::set<vespalib::string> &flags_in) : Blueprint("my_bp"), flags(flags_in) {}
    void set(const vespalib::string &flag) { flags.insert(flag); }
    bool is_set(const vespalib::string &flag) const { return (flags.count(flag) > 0); }
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return std::make_unique<MyBlueprint>(flags); }
    bool setup(const IIndexEnvironment &, const std::vector<vespalib::string> &params) override {
        EXPECT_EQUAL(getName(), "my_bp(foo,bar)");        
        ASSERT_TRUE(params.size() == 2);
        EXPECT_EQUAL(params[0], "foo");
        EXPECT_EQUAL(params[1], "bar");
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

struct Fixture {
    Stash stash;
    IndexEnvironment idx_env;
    QueryEnvironment query_env;
    MyBlueprint blueprint;
    Fixture() : stash(), idx_env(), query_env(&idx_env), blueprint() {}
    IntrinsicExpression::UP create() const {
        return IntrinsicBlueprintAdapter::try_create(blueprint, idx_env, {"foo", "bar"});
    }
};

TEST_F("require that blueprints can be used for intrinsic expressions", Fixture()) {
    auto expression = f1.create();
    ASSERT_TRUE(bool(expression));
    EXPECT_TRUE(!expression->result_type().is_object());
    auto &executor = expression->create_executor(f1.query_env, f1.stash);
    EXPECT_TRUE(dynamic_cast<MyExecutor*>(&executor) != nullptr);
}

TEST_F("require that result type is propagated for intrinsic blueprints", Fixture()) {
    f1.blueprint.set(object_result);
    auto expression = f1.create();
    ASSERT_TRUE(bool(expression));
    EXPECT_TRUE(expression->result_type().is_object());
    EXPECT_TRUE(expression->result_type().type().is_double());
}

TEST_F("require that intrinsic blueprint adaption fails if blueprint setup fails", Fixture()) {
    f1.blueprint.set(fail_setup);
    EXPECT_TRUE(f1.create().get() == nullptr);
}

TEST_F("require that intrinsic blueprint adaption fails if blueprint has inputs", Fixture()) {
    f1.blueprint.set(extra_input);
    EXPECT_TRUE(f1.create().get() == nullptr);
}

TEST_F("require that intrinsic blueprint adaption fails if blueprint has more than one output", Fixture()) {
    f1.blueprint.set(extra_output);
    EXPECT_TRUE(f1.create().get() == nullptr);
}

TEST_F("require that intrinsic blueprint adaption fails if blueprint has no result", Fixture()) {
    f1.blueprint.set(no_output);
    EXPECT_TRUE(f1.create().get() == nullptr);
}

TEST_F("require that intrinsic blueprint adaption fails if blueprint has error typed output", Fixture()) {
    f1.blueprint.set(error_result);
    EXPECT_TRUE(f1.create().get() == nullptr);
}

TEST_MAIN() { TEST_RUN_ALL(); }
