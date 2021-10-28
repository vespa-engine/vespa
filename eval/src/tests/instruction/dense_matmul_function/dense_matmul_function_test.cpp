// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_matmul_function.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

struct FunInfo {
    using LookFor = DenseMatMulFunction;
    size_t lhs_size;
    size_t common_size;
    size_t rhs_size;
    bool lhs_inner;
    bool rhs_inner;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQUAL(fun.lhs_size(), lhs_size);
        EXPECT_EQUAL(fun.common_size(), common_size);
        EXPECT_EQUAL(fun.rhs_size(), rhs_size);
        EXPECT_EQUAL(fun.lhs_common_inner(), lhs_inner);
        EXPECT_EQUAL(fun.rhs_common_inner(), rhs_inner);
    }
};

void verify_optimized(const vespalib::string &expr, FunInfo details) {
    TEST_STATE(expr.c_str());
    CellTypeSpace all_types(CellTypeUtils::list_types(), 2);
    EvalFixture::verify<FunInfo>(expr, {details}, all_types);
}

void verify_not_optimized(const vespalib::string &expr) {
    TEST_STATE(expr.c_str());
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

TEST("require that matmul can be optimized") {
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .lhs_inner = true, .rhs_inner = true };
    TEST_DO(verify_optimized("reduce(a2d3*b5d3,sum,d)", details));
}

TEST("require that matmul with lambda can be optimized") {
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .lhs_inner = true, .rhs_inner = true };
    TEST_DO(verify_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*y)),sum,d)", details));
}

TEST("require that expressions similar to matmul are not optimized") {
    TEST_DO(verify_not_optimized("reduce(a2d3*b5d3,sum,a)"));
    TEST_DO(verify_not_optimized("reduce(a2d3*b5d3,sum,b)"));
    TEST_DO(verify_not_optimized("reduce(a2d3*b5d3,prod,d)"));
    TEST_DO(verify_not_optimized("reduce(a2d3*b5d3,sum)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(y*x)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x+y)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*x)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(y*y)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*y*1)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(a2c3*b5d3,sum,d)"));
    TEST_DO(verify_not_optimized("reduce(a2c3*b5d3,sum,c)"));
}

TEST("require that MatMul can be debug dumped") {
    EvalFixture fixture(prod_factory, "reduce(x*y,sum,d)", EvalFixture::ParamRepo()
                        .add("x", GenSpec::from_desc("a2d3"))
                        .add("y", GenSpec::from_desc("b5d3")), true);
    auto info = fixture.find_all<DenseMatMulFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

TEST("require that matmul inner/inner works correctly") {
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .lhs_inner = true, .rhs_inner = true };
    TEST_DO(verify_optimized("reduce(a2d3*b5d3,sum,d)", details));
    TEST_DO(verify_optimized("reduce(b5d3*a2d3,sum,d)", details));
}

TEST("require that matmul inner/outer works correctly") {
    FunInfo details = { .lhs_size = 2, .common_size = 5, .rhs_size = 3,
                        .lhs_inner = true, .rhs_inner = false };
    TEST_DO(verify_optimized("reduce(a2b5*b5d3,sum,b)", details));
    TEST_DO(verify_optimized("reduce(b5d3*a2b5,sum,b)", details));
}

TEST("require that matmul outer/outer works correctly") {
    FunInfo details = { .lhs_size = 2, .common_size = 5, .rhs_size = 3,
                        .lhs_inner = false, .rhs_inner = false };
    TEST_DO(verify_optimized("reduce(b5c2*b5d3,sum,b)", details));
    TEST_DO(verify_optimized("reduce(b5d3*b5c2,sum,b)", details));
}

TEST_MAIN() { TEST_RUN_ALL(); }
