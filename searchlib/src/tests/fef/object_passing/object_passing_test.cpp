// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/queryenvironment.h>
#include <vespa/searchlib/fef/test/plugin/unbox.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/rank_program.h>
#include <vespa/searchlib/fef/verify_feature.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/searchlib/fef/feature_type.h>

using namespace search::fef;
using namespace search::fef::test;
using namespace search::features;
using vespalib::eval::ValueType;

struct ProxyExecutor : FeatureExecutor {
    bool                      input_is_object;
    bool                      output_is_object;
    double                    number_value;
    vespalib::eval::Value::UP object_value;
    ProxyExecutor(bool input_is_object_in, bool output_is_object_in)
        : input_is_object(input_is_object_in), output_is_object(output_is_object_in),
          number_value(0.0), object_value() {}
    bool isPure() override { return true; }
    void execute(uint32_t) override {
        double was_object = 0.0;
        if (input_is_object) {
            was_object = 1.0;
            number_value = inputs().get_object(0).get().as_double();
            object_value.reset(new vespalib::eval::DoubleValue(number_value));
        } else {
            number_value = inputs().get_number(0);
            object_value.reset(new vespalib::eval::DoubleValue(number_value));
        }
        if (output_is_object) {
            outputs().set_object(0, *object_value);
        } else {
            outputs().set_number(0, number_value);
        }
        outputs().set_number(1, was_object);
    }
};

struct ProxyBlueprint : Blueprint {
    vespalib::string name;
    AcceptInput accept_input;
    bool object_input;
    bool object_output;
    ProxyBlueprint(const vespalib::string &name_in, AcceptInput accept_input_in, bool object_output_in)
        : Blueprint(name_in), name(name_in), accept_input(accept_input_in), object_input(false), object_output(object_output_in) {}
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override {
        return Blueprint::UP(new ProxyBlueprint(name, accept_input, object_output));
    }
    bool setup(const IIndexEnvironment &, const std::vector<vespalib::string> &params) override {
        ASSERT_EQUAL(1u, params.size());
        if (auto input = defineInput(params[0], accept_input)) {
            object_input = input.value().is_object();
            describeOutput("value", "the value", object_output ? FeatureType::object(ValueType::double_type()) : FeatureType::number());
            describeOutput("was_object", "whether input was object", FeatureType::number());
            return true;
        }
        return false;
    }
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override {
        return stash.create<ProxyExecutor>(object_input, object_output);
    }
};

struct Fixture {
    BlueprintFactory factory;
    IndexEnvironment indexEnv;

    explicit Fixture() {
        factory.addPrototype(std::make_shared<ValueBlueprint>());
        factory.addPrototype(std::make_shared<UnboxBlueprint>());
        factory.addPrototype(std::make_shared<ProxyBlueprint>("do_box",      Blueprint::AcceptInput::NUMBER, true));
        factory.addPrototype(std::make_shared<ProxyBlueprint>("do_unbox",    Blueprint::AcceptInput::OBJECT, false));
        factory.addPrototype(std::make_shared<ProxyBlueprint>("maybe_box",   Blueprint::AcceptInput::ANY,    true));
        factory.addPrototype(std::make_shared<ProxyBlueprint>("maybe_unbox", Blueprint::AcceptInput::ANY,    false));
    }

    double eval(const vespalib::string &feature) {
        BlueprintResolver::SP resolver(new BlueprintResolver(factory, indexEnv));
        resolver->addSeed(feature);
        ASSERT_TRUE(resolver->compile());
        MatchDataLayout mdl;
        MatchData::UP md = mdl.createMatchData();
        QueryEnvironment queryEnv(&indexEnv);
        Properties overrides;
        RankProgram program(resolver);
        program.setup(*md, queryEnv, overrides);        
        auto result = program.get_seeds();
        EXPECT_EQUAL(1u, result.num_features());
        EXPECT_TRUE(!result.is_object(0)); // verifies auto-unboxing
        return result.resolve(0).as_number(1);
    }

    bool verify(const vespalib::string &feature) {
        std::vector<search::fef::Message> errors;
        return verifyFeature(factory, indexEnv, feature, "unit test", errors);
    }
};

TEST_F("require that values can be boxed and unboxed", Fixture()) {
    EXPECT_EQUAL(3.0, f1.eval("do_box(value(3))"));
    EXPECT_EQUAL(0.0, f1.eval("do_box(value(3)).was_object"));
    EXPECT_EQUAL(3.0, f1.eval("do_unbox(do_box(value(3)))"));
    EXPECT_EQUAL(1.0, f1.eval("maybe_unbox(do_box(value(3))).was_object"));
    EXPECT_EQUAL(3.0, f1.eval("do_box(do_unbox(do_box(value(3))))"));
    EXPECT_EQUAL(0.0, f1.eval("do_box(do_unbox(do_box(value(3)))).was_object"));
}

TEST_F("require that output features may be either objects or numbers", Fixture()) {
    EXPECT_TRUE(f1.verify("value(3)"));
    EXPECT_TRUE(f1.verify("do_box(value(3))"));
}

TEST_F("require that feature input/output types must be compatible", Fixture()) {
    EXPECT_TRUE(!f1.verify("do_unbox(value(3))"));
    EXPECT_TRUE(f1.verify("maybe_unbox(value(3))"));
    EXPECT_TRUE(f1.verify("do_unbox(do_box(value(3)))"));
    EXPECT_TRUE(!f1.verify("do_unbox(do_box(do_box(value(3))))"));
    EXPECT_TRUE(f1.verify("do_unbox(maybe_box(do_box(value(3))))"));
    EXPECT_TRUE(f1.verify("do_unbox(do_box(do_unbox(do_box(value(3)))))"));
}

TEST_F("require that 'unbox' feature works for both numbers and objects", Fixture()) {
    EXPECT_EQUAL(3.0, f1.eval("unbox(value(3))"));
    EXPECT_EQUAL(3.0, f1.eval("unbox(do_box(value(3)))"));
    EXPECT_EQUAL(0.0, f1.eval("maybe_unbox(unbox(do_box(value(3)))).was_object"));
    EXPECT_EQUAL(0.0, f1.eval("maybe_unbox(unbox(value(3))).was_object"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
