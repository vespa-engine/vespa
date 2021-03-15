// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_xw_product_function.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

GenSpec::seq_t lhs_seq = [] (size_t i) noexcept { return (3.0 + i) * 7.0; };
GenSpec::seq_t rhs_seq = [] (size_t i) noexcept { return (5.0 + i) * 43.0; };

struct FunInfo {
    using LookFor = DenseXWProductFunction;
    size_t vec_size;
    size_t res_size;
    bool happy;
    bool check(const LookFor &fun) const {
        return ((fun.result_is_mutable()) &&
                (fun.vector_size() == vec_size) &&
                (fun.result_size() == res_size) &&
                (fun.common_inner() == happy));
    }
};

void verify(const vespalib::string &expr, const std::vector<FunInfo> &fun_info, const std::vector<CellType> &with_cell_types) {
    auto fun = Function::parse(expr);
    ASSERT_EQUAL(fun->num_params(), 2u);
    vespalib::string lhs_name = fun->param_name(0);
    vespalib::string rhs_name = fun->param_name(1);
    const auto lhs_spec = GenSpec::from_desc(lhs_name);
    const auto rhs_spec = GenSpec::from_desc(rhs_name);
    for (CellType lhs_ct: with_cell_types) {
        for (CellType rhs_ct: with_cell_types) {
            EvalFixture::ParamRepo param_repo;
            param_repo.add(lhs_name, lhs_spec.cpy().cells(lhs_ct).seq(lhs_seq));
            param_repo.add(rhs_name, rhs_spec.cpy().cells(rhs_ct).seq(rhs_seq));
            EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
            EvalFixture fixture(prod_factory, expr, param_repo, true);
            EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
            EXPECT_EQUAL(fixture.result(), slow_fixture.result());
            auto info = fixture.find_all<FunInfo::LookFor>();
            ASSERT_EQUAL(info.size(), fun_info.size());
            for (size_t i = 0; i < fun_info.size(); ++i) {
                EXPECT_TRUE(fun_info[i].check(*info[i]));
            }
        }
    }
}

void verify_not_optimized(const vespalib::string &expr) {
    return verify(expr, {}, {CellType::FLOAT});
}

void verify_optimized(const vespalib::string &expr, size_t vec_size, size_t res_size, bool happy) {
    return verify(expr, {{vec_size, res_size, happy}}, CellTypeUtils::list_types());
}

vespalib::string make_expr(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common) {
    return make_string("reduce(%s*%s,sum,%s)", a.c_str(), b.c_str(), common.c_str());
}

void verify_optimized_multi(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common,
                            size_t vec_size, size_t res_size, bool happy)
{
    {
        auto expr = make_expr(a, b, common);
        TEST_STATE(expr.c_str());
        TEST_DO(verify_optimized(expr, vec_size, res_size, happy));
    }
    {
        auto expr = make_expr(b, a, common);
        TEST_STATE(expr.c_str());
        TEST_DO(verify_optimized(expr, vec_size, res_size, happy));
    }
}

TEST("require that xw product gives same results as reference join/reduce") {
    // 1 -> 1 happy/unhappy
    TEST_DO(verify_optimized_multi("y1", "x1y1", "y", 1, 1, true));
    TEST_DO(verify_optimized_multi("y1", "y1z1", "y", 1, 1, false));
    // 3 -> 2 happy/unhappy
    TEST_DO(verify_optimized_multi("y3", "x2y3", "y", 3, 2, true));
    TEST_DO(verify_optimized_multi("y3", "y3z2", "y", 3, 2, false));
    // 5 -> 8 happy/unhappy
    TEST_DO(verify_optimized_multi("y5", "x8y5", "y", 5, 8, true));
    TEST_DO(verify_optimized_multi("y5", "y5z8", "y", 5, 8, false));
    // 16 -> 5 happy/unhappy
    TEST_DO(verify_optimized_multi("y16", "x5y16", "y", 16, 5, true));
    TEST_DO(verify_optimized_multi("y16", "y16z5", "y", 16, 5, false));
}

TEST("require that various variants of xw product can be optimized") {
    TEST_DO(verify_optimized("reduce(join(y3,x2y3,f(x,y)(x*y)),sum,y)", 3, 2, true));
}

TEST("require that expressions similar to xw product are not optimized") {
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,prod,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,sum)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x+y)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x*x)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*y)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x*1)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2z3,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2z3,sum,z)"));
}

TEST("require that xw product can be debug dumped") {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("y5", GenSpec::from_desc("y5"));
    param_repo.add("x8y5", GenSpec::from_desc("x8y5"));
    EvalFixture fixture(prod_factory, "reduce(y5*x8y5,sum,y)", param_repo, true);
    auto info = fixture.find_all<DenseXWProductFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

TEST_MAIN() { TEST_RUN_ALL(); }
