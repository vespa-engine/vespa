// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <iostream>
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/test/value_compare.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::indexproperties;
using namespace search::fef::test;
using namespace search::features;
using vespalib::eval::DoubleValue;
using vespalib::eval::Function;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace
{

Value::UP make_tensor(const TensorSpec &spec) {
    return SimpleValue::from_spec(spec);
}

}

struct ExecFixture
{
    BlueprintFactory factory;
    FtFeatureTest test;
    ExecFixture(const vespalib::string &feature)
        : factory(),
          test(factory, feature)
    {
        setup_search_features(factory);
    }
    bool setup() { return test.setup(); }
    const Value &extractTensor(uint32_t docid) {
        Value::CREF value = test.resolveObjectFeature(docid);
        ASSERT_TRUE(value.get().type().has_dimensions());
        return value.get();
    }
    const Value &executeTensor(uint32_t docId = 1) {
        return extractTensor(docId);
    }
    double extractDouble(uint32_t docid) {
        Value::CREF value = test.resolveObjectFeature(docid);
        ASSERT_TRUE(value.get().type().is_double());
        return value.get().as_double();
    }
    double executeDouble(uint32_t docId = 1) {
        return extractDouble(docId);
    }
    void addTensor(const vespalib::string &name,
                   const TensorSpec &spec)
    {
        Value::UP tensor = make_tensor(spec);
        ValueType type(tensor->type());
        test.getIndexEnv().addConstantValue(name,
                                            std::move(type),
                                            std::move(tensor));
    }

    void addDouble(const vespalib::string &name, const double value) {
        test.getIndexEnv().addConstantValue(name,
                                            ValueType::double_type(),
                                            std::make_unique<DoubleValue>(value));
    }
};

TEST_F("require that missing constant is detected",
       ExecFixture("constant(foo)"))
{
    EXPECT_TRUE(!f.setup());
}


TEST_F("require that existing tensor constant is detected",
       ExecFixture("constant(foo)"))
{
    f.addTensor("foo",
                TensorSpec("tensor(x{})")
                .add({{"x","a"}}, 3)
                .add({{"x","b"}}, 5)
                .add({{"x","c"}}, 7));
    EXPECT_TRUE(f.setup());
    auto expect = make_tensor(TensorSpec("tensor(x{})")
                              .add({{"x","b"}}, 5)
                              .add({{"x","c"}}, 7)
                              .add({{"x","a"}}, 3));
    EXPECT_EQUAL(*expect, f.executeTensor());
}


TEST_F("require that existing double constant is detected",
       ExecFixture("constant(foo)"))
{
    f.addDouble("foo", 42.0);
    EXPECT_TRUE(f.setup());
    EXPECT_EQUAL(42.0, f.executeDouble());
}


TEST_MAIN() { TEST_RUN_ALL(); }
