// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

GenSpec G(std::vector<std::pair<const char *, size_t>> dims) {
    GenSpec result;
    for (const auto & dim : dims) {
        result.idx(dim.first, dim.second);
    }
    return result;
}

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add_variants("A2B1C3a2d3",       G({{"A",2}, {"B",1}, {"C",3}, {"a",2}, {"d",3}}))  // inner/inner
        .add_variants("A2B1C3D1a2c1d3e1", G({{"A",2}, {"B",1}, {"C",3}, {"D",1}, {"a",2}, {"c",1}, {"d",3}, {"e",1}}))// inner/inner, extra dims
        .add_variants("B1C3a2d3",         G({{"B",1}, {"C",3}, {"a",2}, {"d",3}}))           // inner/inner, missing A
        .add_variants("A1a2d3",           G({{"A",1}, {"a",2}, {"d",3}}))                    // inner/inner, single mat
        .add_variants("A2D3a2b1c3",       G({{"A",2}, {"D",3}, {"a",2}, {"b",1}, {"c",3}}))  // inner/inner, inverted
        .add_variants("A2B1C3a2b5",       G({{"A",2}, {"B",1}, {"C",3}, {"a",2}, {"b",5}}))  // inner/outer
        .add_variants("A2B1C3b5c2",       G({{"A",2}, {"B",1}, {"C",3}, {"b",5}, {"c",2}}))  // outer/outer
        .add_variants("A2B1C3a2c3",       G({{"A",2}, {"B",1}, {"C",3}, {"a",2}, {"c",3}}))  // not matching
        //----------------------------------------------------------------------------------------
        .add_variants("A2B1C3b5d3",       G({{"A",2}, {"B",1}, {"C",3}, {"b",5}, {"d",3}}))  // fixed param
        .add_variants("B1C3b5d3",         G({{"B",1}, {"C",3}, {"b",5}, {"d",3}}))           // fixed param, missing A
        .add_variants("A1b5d3",           G({{"A",1}, {"b",5}, {"d",3}}))                    // fixed param, single mat
        .add_variants("B5D3a2b1c3",       G({{"B",5}, {"D",3}, {"a",2}, {"b",1}, {"c",3}})); // fixed param, inverted
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr,
                      size_t lhs_size, size_t common_size, size_t rhs_size, size_t matmul_cnt,
                      bool lhs_inner, bool rhs_inner)
{
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseMultiMatMulFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQUAL(info[0]->lhs_size(), lhs_size);
    EXPECT_EQUAL(info[0]->common_size(), common_size);
    EXPECT_EQUAL(info[0]->rhs_size(), rhs_size);
    EXPECT_EQUAL(info[0]->matmul_cnt(), matmul_cnt);
    EXPECT_EQUAL(info[0]->lhs_common_inner(), lhs_inner);
    EXPECT_EQUAL(info[0]->rhs_common_inner(), rhs_inner);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseMultiMatMulFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that multi matmul can be optimized") {
    TEST_DO(verify_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3,sum,d)", 2, 3, 5, 6, true, true));
}

TEST("require that single multi matmul can be optimized") {
    TEST_DO(verify_optimized("reduce(A1a2d3*A1b5d3,sum,d)", 2, 3, 5, 1, true, true));
}

TEST("require that multi matmul with lambda can be optimized") {
    TEST_DO(verify_optimized("reduce(join(A2B1C3a2d3,A2B1C3b5d3,f(x,y)(x*y)),sum,d)", 2, 3, 5, 6, true, true));
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

TEST("require that multi matmul must have matching cell type") {
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2d3_f*A2B1C3b5d3,sum,d)"));
    TEST_DO(verify_not_optimized("reduce(A2B1C3a2d3*A2B1C3b5d3_f,sum,d)"));
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
    TEST_DO(verify_optimized("reduce(A2B1C3D1a2c1d3e1*A2B1C3b5d3,sum,d)", 2, 3, 5, 6, true, true));
    TEST_DO(verify_optimized("reduce(A2B1C3b5d3*A2B1C3D1a2c1d3e1,sum,d)", 2, 3, 5, 6, true, true));
}

TEST("require that multi matmul function can be debug dumped") {
    EvalFixture fixture(prod_factory, "reduce(A2B1C3a2d3*A2B1C3b5d3,sum,d)", param_repo, true);
    auto info = fixture.find_all<DenseMultiMatMulFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

vespalib::string make_expr(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common,
                           bool float_cells)
{
    return make_string("reduce(%s%s*%s%s,sum,%s)", a.c_str(), float_cells ? "_f" : "", b.c_str(), float_cells ? "_f" : "", common.c_str());
}

void verify_optimized_multi(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common,
                            size_t lhs_size, size_t common_size, size_t rhs_size, size_t matmul_cnt,
                            bool lhs_inner, bool rhs_inner)
{
    for (bool float_cells: {false, true}) {
        {
            auto expr = make_expr(a, b, common, float_cells);
            TEST_STATE(expr.c_str());
            TEST_DO(verify_optimized(expr, lhs_size, common_size, rhs_size, matmul_cnt, lhs_inner, rhs_inner));
        }
        {
            auto expr = make_expr(b, a, common, float_cells);
            TEST_STATE(expr.c_str());
            TEST_DO(verify_optimized(expr, lhs_size, common_size, rhs_size, matmul_cnt, lhs_inner, rhs_inner));
        }
    }
}

TEST("require that multi matmul inner/inner works correctly") {
    TEST_DO(verify_optimized_multi("A2B1C3a2d3", "A2B1C3b5d3", "d", 2, 3, 5, 6, true, true));
}

TEST("require that multi matmul inner/outer works correctly") {
    TEST_DO(verify_optimized_multi("A2B1C3a2b5", "A2B1C3b5d3", "b", 2, 5, 3, 6, true, false));
}

TEST("require that multi matmul outer/outer works correctly") {
    TEST_DO(verify_optimized_multi("A2B1C3b5c2", "A2B1C3b5d3", "b", 2, 5, 3, 6, false, false));
}

TEST_MAIN() { TEST_RUN_ALL(); }
