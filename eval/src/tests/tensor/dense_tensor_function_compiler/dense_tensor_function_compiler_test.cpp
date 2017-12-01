// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/dense/dense_dot_product_function.h>
#include <vespa/eval/tensor/dense/dense_xw_product_function.h>
#include <vespa/eval/tensor/dense/dense_tensor_function_compiler.h>
#include <vespa/eval/eval/operation.h>

using namespace vespalib::eval;
using namespace vespalib::eval::operation;
using namespace vespalib::eval::tensor_function;
using namespace vespalib::tensor;
using vespalib::Stash;

//-----------------------------------------------------------------------------

const TensorFunction &
compileDotProduct(const vespalib::string &lhsType,
                  const vespalib::string &rhsType,
                  Stash &stash)
{
    const Node &reduceNode = reduce(join(inject(ValueType::from_spec(lhsType), 1, stash),
                                         inject(ValueType::from_spec(rhsType), 3, stash),
                                         Mul::f, stash),
                                    Aggr::SUM, {}, stash);
    return DenseTensorFunctionCompiler::compile(reduceNode, stash);
}

void
assertCompiledDotProduct(const vespalib::string &lhsType,
                         const vespalib::string &rhsType)
{
    Stash stash;
    const TensorFunction &func = compileDotProduct(lhsType, rhsType, stash);
    const DenseDotProductFunction *dotProduct = as<DenseDotProductFunction>(func);
    ASSERT_TRUE(dotProduct);
    EXPECT_EQUAL(1u, dotProduct->lhsTensorId());
    EXPECT_EQUAL(3u, dotProduct->rhsTensorId());
}

void
assertNotCompiledDotProduct(const vespalib::string &lhsType,
                            const vespalib::string &rhsType)
{
    Stash stash;
    const TensorFunction &func = compileDotProduct(lhsType, rhsType, stash);
    const Reduce *reduce = as<Reduce>(func);
    EXPECT_TRUE(reduce);
}

//-----------------------------------------------------------------------------

const TensorFunction &
compileXWProduct(const vespalib::string &lhsType,
                 const vespalib::string &rhsType,
                 const vespalib::string &dim,
                 Stash &stash)
{
    const Node &reduceNode = reduce(join(inject(ValueType::from_spec(lhsType), 1, stash),
                                         inject(ValueType::from_spec(rhsType), 3, stash),
                                         Mul::f, stash),
                                    Aggr::SUM, {dim}, stash);
    return DenseTensorFunctionCompiler::compile(reduceNode, stash);
}

void
assertCompiledXWProduct(const vespalib::string &vecTypeStr,
                        const vespalib::string &matTypeStr,
                        const vespalib::string &dim)
{
    Stash stash;
    const TensorFunction &func = compileXWProduct(vecTypeStr, matTypeStr, dim, stash);
    const TensorFunction &inv_func = compileXWProduct(matTypeStr, vecTypeStr, dim, stash);
    const DenseXWProductFunction *xwProduct = as<DenseXWProductFunction>(func);
    const DenseXWProductFunction *inv_xwProduct = as<DenseXWProductFunction>(inv_func);
    ValueType vecType = ValueType::from_spec(vecTypeStr);
    ValueType matType = ValueType::from_spec(matTypeStr);
    size_t common_idx = matType.dimension_index(vecType.dimensions()[0].name);
    ASSERT_TRUE(xwProduct);
    ASSERT_TRUE(inv_xwProduct);
    ASSERT_TRUE(common_idx != ValueType::Dimension::npos);
    EXPECT_EQUAL(xwProduct->vectorId(), 1u);
    EXPECT_EQUAL(inv_xwProduct->vectorId(), 3u);
    EXPECT_EQUAL(xwProduct->matrixId(), 3u);
    EXPECT_EQUAL(inv_xwProduct->matrixId(), 1u);
    EXPECT_EQUAL(xwProduct->vectorSize(), vecType.dimensions()[0].size);
    EXPECT_EQUAL(inv_xwProduct->vectorSize(), vecType.dimensions()[0].size);
    EXPECT_EQUAL(xwProduct->resultSize(), matType.dimensions()[1 - common_idx].size);
    EXPECT_EQUAL(inv_xwProduct->resultSize(), matType.dimensions()[1 - common_idx].size);
    EXPECT_EQUAL(xwProduct->matrixHasCommonDimensionInnermost(), (common_idx == 1));
    EXPECT_EQUAL(inv_xwProduct->matrixHasCommonDimensionInnermost(), (common_idx == 1));
}

void
assertNotCompiledXWProduct(const vespalib::string &vecType,
                           const vespalib::string &matType,
                           const vespalib::string &dim)
{
    Stash stash;
    const TensorFunction &func = compileXWProduct(vecType, matType, dim, stash);
    const TensorFunction &inv_func = compileXWProduct(matType, vecType, dim, stash);
    const Reduce *reduce = as<Reduce>(func);
    const Reduce *inv_reduce = as<Reduce>(inv_func);
    EXPECT_TRUE(reduce);
    EXPECT_TRUE(inv_reduce);
}

//-----------------------------------------------------------------------------

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

//-----------------------------------------------------------------------------

TEST("require that xw products with compatible dimensions are compiled") {
    TEST_DO(assertCompiledXWProduct("tensor(x[3])", "tensor(x[3],y[4])", "x"));
    TEST_DO(assertCompiledXWProduct("tensor(y[4])", "tensor(x[3],y[4])", "y"));
}

TEST("require that xw products with incompatible dimensions are not compiled") {
    TEST_DO(assertNotCompiledXWProduct("tensor(x[3])", "tensor(x[3],y[4])", "y"));
    TEST_DO(assertNotCompiledXWProduct("tensor(x[])",  "tensor(x[3],y[4])", "x"));
    TEST_DO(assertNotCompiledXWProduct("tensor(x[3])", "tensor(x[],y[4])",  "x"));
    TEST_DO(assertNotCompiledXWProduct("tensor(x[3])", "tensor(x[3],y[])",  "x"));
    TEST_DO(assertNotCompiledXWProduct("tensor(x[2])", "tensor(x[3],y[4])", "x"));
    TEST_DO(assertNotCompiledXWProduct("tensor(x[4])", "tensor(x[3],y[4])", "x"));
    TEST_DO(assertNotCompiledXWProduct("tensor(x[3])", "tensor(y[3],z[4])", "x"));
    TEST_DO(assertNotCompiledXWProduct("tensor(x[3])", "tensor(y[3],z[4])", "y"));
    TEST_DO(assertNotCompiledXWProduct("tensor(x[3])", "tensor(y[3],z[4])", "z"));
    TEST_DO(assertNotCompiledXWProduct("tensor(y[4])", "tensor(x[3],y[4])", "x"));
    TEST_DO(assertNotCompiledXWProduct("tensor(y[3])", "tensor(x[3],y[4])", "y"));
    TEST_DO(assertNotCompiledXWProduct("tensor(y[5])", "tensor(x[3],y[4])", "y"));
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
