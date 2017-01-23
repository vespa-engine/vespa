// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/fef/test/ftlib.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/tensor_factory.h>

using search::feature_t;
using namespace search::fef;
using namespace search::fef::indexproperties;
using namespace search::fef::test;
using namespace search::features;
using vespalib::eval::Function;
using vespalib::eval::Value;
using vespalib::eval::DoubleValue;
using vespalib::eval::TensorValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::tensor::DefaultTensorEngine;
using vespalib::tensor::DenseTensorCells;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorCells;
using vespalib::tensor::TensorDimensions;
using vespalib::tensor::TensorFactory;

namespace
{

Tensor::UP createTensor(const TensorCells &cells,
                        const TensorDimensions &dimensions) {
    vespalib::tensor::DefaultTensor::builder builder;
    return TensorFactory::create(cells, dimensions, builder);
}

Tensor::UP make_tensor(const TensorSpec &spec) {
    auto tensor = DefaultTensorEngine::ref().create(spec);
    return Tensor::UP(dynamic_cast<Tensor*>(tensor.release()));
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
    const Tensor &extractTensor() {
        const Value::CREF *value = test.resolveObjectFeature();
        ASSERT_TRUE(value != nullptr);
        ASSERT_TRUE(value->get().is_tensor());
        return static_cast<const Tensor &>(*value->get().as_tensor());
    }
    const Tensor &executeTensor(uint32_t docId = 1) {
        test.executeOnly(docId);
        return extractTensor();
    }
    double extractDouble() {
        const Value::CREF *value = test.resolveObjectFeature();
        ASSERT_TRUE(value != nullptr);
        ASSERT_TRUE(value->get().is_double());
        return value->get().as_double();
    }
    double executeDouble(uint32_t docId = 1) {
        test.executeOnly(docId);
        return extractDouble();
    }
    void addTensor(const vespalib::string &name,
                   const TensorCells &cells,
                   const TensorDimensions &dimensions)
    {
        Tensor::UP tensor = createTensor(cells, dimensions);
        ValueType type(tensor->getType());
        test.getIndexEnv().addConstantValue(name,
                                            std::move(type),
                                            std::make_unique<TensorValue>(std::move(tensor)));
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
                {   {{{"x", "a"}}, 3},
                    {{{"x", "b"}}, 5},
                    {{{"x", "c"}}, 7} },
                { "x" });
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
