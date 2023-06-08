// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_multi_matmul_function.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

struct FunInfo {
    using LookFor = DenseMultiMatMulFunction;
    size_t lhs_size;
    size_t common_size;
    size_t rhs_size;
    size_t matmul_cnt;
    bool lhs_inner;
    bool rhs_inner;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQUAL(fun.lhs_size(), lhs_size);
        EXPECT_EQUAL(fun.common_size(), common_size);
        EXPECT_EQUAL(fun.rhs_size(), rhs_size);
        EXPECT_EQUAL(fun.matmul_cnt(), matmul_cnt);
        EXPECT_EQUAL(fun.lhs_common_inner(), lhs_inner);
        EXPECT_EQUAL(fun.rhs_common_inner(), rhs_inner);
    }
};

void verify_optimized(const vespalib::string &expr, const FunInfo &details)
{
    TEST_STATE(expr.c_str());
    CellTypeSpace stable_types(CellTypeUtils::list_stable_types(), 2);
    CellTypeSpace unstable_types(CellTypeUtils::list_unstable_types(), 2);
    EvalFixture::verify<FunInfo>(expr, {details}, CellTypeSpace(stable_types).same());
    EvalFixture::verify<FunInfo>(expr, {}, CellTypeSpace(std::move(stable_types)).different());
    EvalFixture::verify<FunInfo>(expr, {}, std::move(unstable_types));
}

void verify_not_optimized(const vespalib::string &expr) {
    TEST_STATE(expr.c_str());
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

TEST("require that multi matmul can be optimized") {
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .matmul_cnt = 6, .lhs_inner = true, .rhs_inner = true };
    TEST_DO(verify_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3,sum,d)", details));
    TEST_DO(verify_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3,sum,d)", details));
}

TEST("require that single multi matmul can be optimized") {
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .matmul_cnt = 1, .lhs_inner = true, .rhs_inner = true };
    TEST_DO(verify_optimized("reduce(A1a2d3*A1b5d3,sum,d)", details));
}

TEST("require that multi matmul with lambda can be optimized") {
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .matmul_cnt = 6, .lhs_inner = true, .rhs_inner = true };
    TEST_DO(verify_optimized("reduce(join(A2B1C3a2d3,A2B1C3b5d3,f(x,y)(x*y)),sum,d)", details));
}

TEST("require that expressions similar to multi matmul are not optimized") {
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3,sum,a)"));
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3,sum,b)"));
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3,prod,d)"));
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3,sum)"));
    TEST_DO(verify_not_optimized("reduce(join(A2B1C3a2d3,A2B1C3b5d3,f(x,y)(y*x)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(A2B1C3a2d3,A2B1C3b5d3,f(x,y)(x+y)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(A2B1C3a2d3,A2B1C3b5d3,f(x,y)(x*x)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(A2B1C3a2d3,A2B1C3b5d3,f(x,y)(y*y)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(A2B1C3a2d3,A2B1C3b5d3,f(x,y)(x*y*1)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2c3*A2B1C3b5d3,sum,d)"));
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2c3*A2B1C3b5d3,sum,c)"));
}

TEST("require that multi matmul must have matching dimension prefix") {
    TEST_DO(verify_not_optimized("reduce(B1C3a2d3*A2B1C3b5d3,sum,d)"));
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2d3*B1C3b5d3,sum,d)"));
}

TEST("require that multi matmul must have inner nesting of matmul dimensions") {
    TEST_DO(verify_not_optimized("reduce(A2D3a2b1c3*B5D3a2b1c3,sum,D)"));
    TEST_DO(verify_not_optimized("reduce(B5D3a2b1c3*A2D3a2b1c3,sum,D)"));
}

TEST("require that multi matmul ignores trivial dimensions") {
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .matmul_cnt = 6, .lhs_inner = true, .rhs_inner = true };
    TEST_DO(verify_optimized("reduce(A2B1C3D1a2c1d3e1*A2B1C3b5d3,sum,d)", details));
    TEST_DO(verify_optimized("reduce(A2B1C3b5d3*A2B1C3D1a2c1d3e1,sum,d)", details));
}

TEST("require that multi matmul function can be debug dumped") {
    EvalFixture fixture(prod_factory, "reduce(m1*m2,sum,d)", EvalFixture::ParamRepo()
                        .add("m1", GenSpec::from_desc("A2B1C3a2d3"))
                        .add("m2", GenSpec::from_desc("A2B1C3b5d3")), true);
    auto info = fixture.find_all<DenseMultiMatMulFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

TEST("require that multi matmul inner/inner works correctly") {
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .matmul_cnt = 6, .lhs_inner = true, .rhs_inner = true };
    TEST_DO(verify_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3,sum,d)", details));
}

TEST("require that multi matmul inner/outer works correctly") {
    FunInfo details = { .lhs_size = 2, .common_size = 5, .rhs_size = 3,
                        .matmul_cnt = 6, .lhs_inner = true, .rhs_inner = false };
    TEST_DO(verify_optimized("reduce(A2B1C3a2b5*A2B1C3b5d3,sum,b)", details));
}

TEST("require that multi matmul outer/outer works correctly") {
    FunInfo details = { .lhs_size = 2, .common_size = 5, .rhs_size = 3,
                        .matmul_cnt = 6, .lhs_inner = false, .rhs_inner = false };
    TEST_DO(verify_optimized("reduce(A2B1C3b5c2*A2B1C3b5d3,sum,b)", details));
}

TEST_MAIN() { TEST_RUN_ALL(); }
