// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/node_types.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::indexproperties;
using namespace search::fef::test;
using namespace search::features;
using vespalib::eval::DoubleValue;
using vespalib::eval::Function;
using vespalib::eval::SimpleValue;
using vespalib::eval::NodeTypes;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::make_string_short::fmt;

namespace {

Value::UP make_tensor(const TensorSpec &spec) {
    return SimpleValue::from_spec(spec);
}

}

class ConstantTest : public ::testing::Test
{
protected:
    BlueprintFactory factory;
    FtFeatureTest test;
    ConstantTest();
    ~ConstantTest() override;
    bool setup() { return test.setup(); }
    const Value &extractTensor(uint32_t docid) {
        Value::CREF value = test.resolveObjectFeature(docid);
        EXPECT_TRUE(value.get().type().has_dimensions());
        return value.get();
    }
    const Value &executeTensor(uint32_t docId = 1) {
        return extractTensor(docId);
    }
    double extractDouble(uint32_t docid) {
        Value::CREF value = test.resolveObjectFeature(docid);
        EXPECT_TRUE(value.get().type().is_double());
        return value.get().as_double();
    }
    double executeDouble(uint32_t docId = 1) {
        return extractDouble(docId);
    }
    void addTensor(const std::string &name,
                   const TensorSpec &spec)
    {
        Value::UP tensor = make_tensor(spec);
        ValueType type(tensor->type());
        test.getIndexEnv().addConstantValue(name,
                                            std::move(type),
                                            std::move(tensor));
    }
    void addDouble(const std::string &name, const double value) {
        test.getIndexEnv().addConstantValue(name,
                                            ValueType::double_type(),
                                            std::make_unique<DoubleValue>(value));
    }
    void addTypeValue(const std::string &name, const std::string &type, const std::string &value) {
        auto &props = test.getIndexEnv().getProperties();
        auto type_prop = fmt("constant(%s).type", name.c_str());
        auto value_prop = fmt("constant(%s).value", name.c_str());
        props.add(type_prop, type);
        props.add(value_prop, value);
    }
};

ConstantTest::ConstantTest()
    : ::testing::Test(),
      factory(),
      test(factory, "constant(foo)")
{
    setup_search_features(factory);
}

ConstantTest::~ConstantTest() = default;

TEST_F(ConstantTest, require_that_missing_constant_is_detected)
{
    EXPECT_TRUE(!setup());
}


TEST_F(ConstantTest, require_that_existing_tensor_constant_is_detected)
{
    addTensor("foo",
              TensorSpec("tensor(x{})")
              .add({{"x","a"}}, 3)
              .add({{"x","b"}}, 5)
              .add({{"x","c"}}, 7));
    EXPECT_TRUE(setup());
    auto expect = make_tensor(TensorSpec("tensor(x{})")
                              .add({{"x","b"}}, 5)
                              .add({{"x","c"}}, 7)
                              .add({{"x","a"}}, 3));
    EXPECT_EQ(*expect, executeTensor());
}


TEST_F(ConstantTest, require_that_existing_double_constant_is_detected)
{
    addDouble("foo", 42.0);
    EXPECT_TRUE(setup());
    EXPECT_EQ(42.0, executeDouble());
}

//-----------------------------------------------------------------------------

TEST_F(ConstantTest, require_that_constants_can_be_functional) {
    addTypeValue("foo", "tensor(x{})", "tensor(x{}):{a:3,b:5,c:7}");
    EXPECT_TRUE(setup());
    auto expect = make_tensor(TensorSpec("tensor(x{})")
                              .add({{"x","b"}}, 5)
                              .add({{"x","c"}}, 7)
                              .add({{"x","a"}}, 3));
    EXPECT_EQ(*expect, executeTensor());
}

TEST_F(ConstantTest, require_that_functional_constant_type_must_match_the_expression_result) {
    addTypeValue("foo", "tensor<float>(x{})", "tensor(x{}):{a:3,b:5,c:7}");
    EXPECT_TRUE(!setup());
}

TEST_F(ConstantTest, require_that_functional_constant_must_parse_without_errors) {
    addTypeValue("foo", "double", "this is parse error");
    EXPECT_TRUE(!setup());
}

TEST_F(ConstantTest, require_that_non_const_functional_constant_is_not_allowed) {
    addTypeValue("foo", "tensor(x{})", "tensor(x{}):{a:a,b:5,c:7}");
    EXPECT_TRUE(!setup());
}

TEST_F(ConstantTest, require_that_functional_constant_must_have_non_error_type) {
    addTypeValue("foo", "error", "impossible to create value with error type");
    EXPECT_TRUE(!setup());
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
