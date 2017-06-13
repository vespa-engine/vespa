// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/dense/dense_dot_product_function.h>
#include <vespa/eval/tensor/dense/dense_tensor_function_compiler.h>

using namespace vespalib::eval;
using namespace vespalib::eval::operation;
using namespace vespalib::eval::tensor_function;
using namespace vespalib::tensor;

template <typename T>
const T *as(const TensorFunction &function) { return dynamic_cast<const T *>(&function); }

TensorFunction::UP
compileDotProduct(const vespalib::string &lhsType,
                  const vespalib::string &rhsType)
{
    Node_UP reduceNode = reduce(apply(Mul(),
                                      inject(ValueType::from_spec(lhsType), 1),
                                      inject(ValueType::from_spec(rhsType), 3)),
                                Add(), {});
    return DenseTensorFunctionCompiler::compile(std::move(reduceNode));
}

void
assertCompiledDotProduct(const vespalib::string &lhsType,
                         const vespalib::string &rhsType)
{
    TensorFunction::UP func = compileDotProduct(lhsType, rhsType);
    const DenseDotProductFunction *dotProduct = as<DenseDotProductFunction>(*func);
    ASSERT_TRUE(dotProduct);
    EXPECT_EQUAL(1u, dotProduct->lhsTensorId());
    EXPECT_EQUAL(3u, dotProduct->rhsTensorId());
}

void
assertNotCompiledDotProduct(const vespalib::string &lhsType,
                            const vespalib::string &rhsType)
{
    TensorFunction::UP func = compileDotProduct(lhsType, rhsType);
    const Reduce *reduce = as<Reduce>(*func);
    EXPECT_TRUE(reduce);
}

TEST("require that dot product with compatible dimensions is compiled")
{
    TEST_DO(assertCompiledDotProduct("tensor(x[5])", "tensor(x[5])"));
    TEST_DO(assertCompiledDotProduct("tensor(x[3])", "tensor(x[5])"));
    TEST_DO(assertCompiledDotProduct("tensor(x[5])", "tensor(x[3])"));
    TEST_DO(assertCompiledDotProduct("tensor(x[])",  "tensor(x[5])"));
    TEST_DO(assertCompiledDotProduct("tensor(x[5])", "tensor(x[])"));
    TEST_DO(assertCompiledDotProduct("tensor(x[])",  "tensor(x[])"));
}

TEST("require that dot product with incompatible dimensions is NOT compiled")
{
    TEST_DO(assertNotCompiledDotProduct("tensor(x[5])",      "tensor(y[5])"));
    TEST_DO(assertNotCompiledDotProduct("tensor(y[5])",      "tensor(x[5])"));
    TEST_DO(assertNotCompiledDotProduct("tensor(y[])",       "tensor(x[])"));
    TEST_DO(assertNotCompiledDotProduct("tensor(x[5])",      "tensor(x[5],y[7])"));
    TEST_DO(assertNotCompiledDotProduct("tensor(x[5],y[7])", "tensor(x[5],y[7])"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
