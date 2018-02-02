// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/dense/dense_dot_product_function.h>
#include <vespa/eval/tensor/dense/dense_xw_product_function.h>
#include <vespa/eval/eval/operation.h>

using namespace vespalib::eval;
using namespace vespalib::eval::operation;
using namespace vespalib::eval::tensor_function;
using namespace vespalib::tensor;
using vespalib::Stash;

//-----------------------------------------------------------------------------

const TensorFunction &
optimizeDotProduct(const vespalib::string &lhsType,
                  const vespalib::string &rhsType,
                  Stash &stash)
{
    const Node &reduceNode = reduce(join(inject(ValueType::from_spec(lhsType), 1, stash),
                                         inject(ValueType::from_spec(rhsType), 3, stash),
                                         Mul::f, stash),
                                    Aggr::SUM, {}, stash);
    return DenseDotProductFunction::optimize(reduceNode, stash);
}

void assertParam(const TensorFunction &node, size_t expect_idx) {
    auto inject = as<Inject>(node);
    ASSERT_TRUE(inject);
    EXPECT_EQUAL(inject->param_idx(), expect_idx);
}

void
assertOptimizedDotProduct(const vespalib::string &lhsType,
                          const vespalib::string &rhsType)
{
    Stash stash;
    const TensorFunction &func = optimizeDotProduct(lhsType, rhsType, stash);
    const DenseDotProductFunction *dotProduct = as<DenseDotProductFunction>(func);
    ASSERT_TRUE(dotProduct);
    TEST_DO(assertParam(dotProduct->lhs(), 1));
    TEST_DO(assertParam(dotProduct->rhs(), 3));
}

void
assertNotOptimizedDotProduct(const vespalib::string &lhsType,
                            const vespalib::string &rhsType)
{
    Stash stash;
    const TensorFunction &func = optimizeDotProduct(lhsType, rhsType, stash);
    const Reduce *reduce = as<Reduce>(func);
    EXPECT_TRUE(reduce);
}

//-----------------------------------------------------------------------------

const TensorFunction &
optimizeXWProduct(const vespalib::string &lhsType,
                 const vespalib::string &rhsType,
                 const vespalib::string &dim,
                 Stash &stash)
{
    const Node &reduceNode = reduce(join(inject(ValueType::from_spec(lhsType), 1, stash),
                                         inject(ValueType::from_spec(rhsType), 3, stash),
                                         Mul::f, stash),
                                    Aggr::SUM, {dim}, stash);
    return DenseXWProductFunction::optimize(reduceNode, stash);
}

void
assertOptimizedXWProduct(const vespalib::string &vecTypeStr,
                        const vespalib::string &matTypeStr,
                        const vespalib::string &dim)
{
    Stash stash;
    const TensorFunction &func = optimizeXWProduct(vecTypeStr, matTypeStr, dim, stash);
    const TensorFunction &inv_func = optimizeXWProduct(matTypeStr, vecTypeStr, dim, stash);
    const DenseXWProductFunction *xwProduct = as<DenseXWProductFunction>(func);
    const DenseXWProductFunction *inv_xwProduct = as<DenseXWProductFunction>(inv_func);
    ValueType vecType = ValueType::from_spec(vecTypeStr);
    ValueType matType = ValueType::from_spec(matTypeStr);
    size_t common_idx = matType.dimension_index(vecType.dimensions()[0].name);
    ASSERT_TRUE(xwProduct);
    ASSERT_TRUE(inv_xwProduct);
    ASSERT_TRUE(common_idx != ValueType::Dimension::npos);
    TEST_DO(assertParam(xwProduct->lhs(), 1));
    TEST_DO(assertParam(inv_xwProduct->lhs(), 3));
    TEST_DO(assertParam(xwProduct->rhs(), 3));
    TEST_DO(assertParam(inv_xwProduct->rhs(), 1));
    EXPECT_EQUAL(xwProduct->vectorSize(), vecType.dimensions()[0].size);
    EXPECT_EQUAL(inv_xwProduct->vectorSize(), vecType.dimensions()[0].size);
    EXPECT_EQUAL(xwProduct->resultSize(), matType.dimensions()[1 - common_idx].size);
    EXPECT_EQUAL(inv_xwProduct->resultSize(), matType.dimensions()[1 - common_idx].size);
    EXPECT_EQUAL(xwProduct->matrixHasCommonDimensionInnermost(), (common_idx == 1));
    EXPECT_EQUAL(inv_xwProduct->matrixHasCommonDimensionInnermost(), (common_idx == 1));
}

void
assertNotOptimizedXWProduct(const vespalib::string &vecType,
                           const vespalib::string &matType,
                           const vespalib::string &dim)
{
    Stash stash;
    const TensorFunction &func = optimizeXWProduct(vecType, matType, dim, stash);
    const TensorFunction &inv_func = optimizeXWProduct(matType, vecType, dim, stash);
    const Reduce *reduce = as<Reduce>(func);
    const Reduce *inv_reduce = as<Reduce>(inv_func);
    EXPECT_TRUE(reduce);
    EXPECT_TRUE(inv_reduce);
}

//-----------------------------------------------------------------------------

TEST("require that dot product with compatible dimensions is optimized")
{
    TEST_DO(assertOptimizedDotProduct("tensor(x[5])", "tensor(x[5])"));
    TEST_DO(assertOptimizedDotProduct("tensor(x[3])", "tensor(x[5])"));
    TEST_DO(assertOptimizedDotProduct("tensor(x[5])", "tensor(x[3])"));
    TEST_DO(assertOptimizedDotProduct("tensor(x[])",  "tensor(x[5])"));
    TEST_DO(assertOptimizedDotProduct("tensor(x[5])", "tensor(x[])"));
    TEST_DO(assertOptimizedDotProduct("tensor(x[])",  "tensor(x[])"));
}

TEST("require that dot product with incompatible dimensions is NOT optimized")
{
    TEST_DO(assertNotOptimizedDotProduct("tensor(x[5])",      "tensor(y[5])"));
    TEST_DO(assertNotOptimizedDotProduct("tensor(y[5])",      "tensor(x[5])"));
    TEST_DO(assertNotOptimizedDotProduct("tensor(y[])",       "tensor(x[])"));
    TEST_DO(assertNotOptimizedDotProduct("tensor(x[5])",      "tensor(x[5],y[7])"));
    TEST_DO(assertNotOptimizedDotProduct("tensor(x[5],y[7])", "tensor(x[5],y[7])"));
}

//-----------------------------------------------------------------------------

TEST("require that xw products with compatible dimensions are optimized") {
    TEST_DO(assertOptimizedXWProduct("tensor(x[3])", "tensor(x[3],y[4])", "x"));
    TEST_DO(assertOptimizedXWProduct("tensor(y[4])", "tensor(x[3],y[4])", "y"));
}

TEST("require that xw products with incompatible dimensions are not optimized") {
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[3])", "tensor(x[3],y[4])", "y"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[])",  "tensor(x[3],y[4])", "x"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[3])", "tensor(x[],y[4])",  "x"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[3])", "tensor(x[3],y[])",  "x"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[2])", "tensor(x[3],y[4])", "x"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[4])", "tensor(x[3],y[4])", "x"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[3])", "tensor(y[3],z[4])", "x"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[3])", "tensor(y[3],z[4])", "y"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(x[3])", "tensor(y[3],z[4])", "z"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(y[4])", "tensor(x[3],y[4])", "x"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(y[3])", "tensor(x[3],y[4])", "y"));
    TEST_DO(assertNotOptimizedXWProduct("tensor(y[5])", "tensor(x[3],y[4])", "y"));
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
