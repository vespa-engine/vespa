// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_single_reduce_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/eval/eval/test/eval_fixture.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::tensor;
using namespace vespalib::eval::tensor_function;

const TensorEngine &prod_engine = DefaultTensorEngine::ref();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add_dense({{"a", 2}, {"b", 3}, {"c", 4}, {"d", 5}})
        .add_cube("a", 2, "b", 1, "c", 1)
        .add_cube("a", 1, "b", 2, "c", 1)
        .add_cube("a", 1, "b", 1, "c", 2)
        .add_cube("a", 1, "b", 1, "c", 1)
        .add_vector("a", 10)
        .add("xy_mapped", spec({x({"a", "b"}),y({"x", "y"})}, N()))
        .add("xyz_mixed", spec({x({"a", "b"}),y({"x", "y"}),z(3)}, N()));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, size_t dim_idx, Aggr aggr)
{
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSingleReduceFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQUAL(info[0]->dim_idx(), dim_idx);
    EXPECT_EQUAL(int(info[0]->aggr()), int(aggr));
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSingleReduceFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that multi-dimensional reduce is not optimized") {
    TEST_DO(verify_not_optimized("reduce(a2b3c4d5,sum,a,b)"));
    TEST_DO(verify_not_optimized("reduce(a2b3c4d5,sum,c,d)"));
}

TEST("require that reduce to scalar is not optimized") {
    TEST_DO(verify_not_optimized("reduce(a10,sum,a)"));
    TEST_DO(verify_not_optimized("reduce(a10,sum)"));
}

TEST("require that sparse reduce is not optimized") {
    TEST_DO(verify_not_optimized("reduce(xy_mapped,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(xy_mapped,sum,y)"));
}

TEST("require that mixed reduce is not optimized") {
    TEST_DO(verify_not_optimized("reduce(xyz_mixed,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(xyz_mixed,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(xyz_mixed,sum,z)"));
}

// NB: these are shadowed by the remove dimension optimization
TEST("require that reducing self-aggregating trivial dimensions is not optimized") {
    TEST_DO(verify_not_optimized("reduce(a1b1c1,avg,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,prod,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,sum,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,max,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,min,c)"));
}

TEST("require that reducing trivial dimension with COUNT is 'optimized'") {
    TEST_DO(verify_optimized("reduce(a1b1c1,count,a)", 0, Aggr::COUNT));
    TEST_DO(verify_optimized("reduce(a1b1c1,count,b)", 1, Aggr::COUNT));
    TEST_DO(verify_optimized("reduce(a1b1c1,count,c)", 2, Aggr::COUNT));
}

vespalib::string make_expr(const vespalib::string &arg, const vespalib::string &dim, bool float_cells, Aggr aggr) {
    return make_string("reduce(%s%s,%s,%s)", arg.c_str(), float_cells ? "f" : "", AggrNames::name_of(aggr)->c_str(), dim.c_str());
}

void verify_optimized_multi(const vespalib::string &arg, const vespalib::string &dim, size_t dim_idx) {
    for (bool float_cells: {false, true}) {
        for (Aggr aggr: Aggregator::list()) {
            auto expr = make_expr(arg, dim, float_cells, aggr);
            TEST_DO(verify_optimized(expr, dim_idx, aggr));
        }
    }
}

TEST("require that normal dense single reduce works") {
    TEST_DO(verify_optimized_multi("a2b3c4d5", "a", 0));
    TEST_DO(verify_optimized_multi("a2b3c4d5", "b", 1));
    TEST_DO(verify_optimized_multi("a2b3c4d5", "c", 2));
    TEST_DO(verify_optimized_multi("a2b3c4d5", "d", 3));
}

TEST("require that minimal dense single reduce works") {
    TEST_DO(verify_optimized_multi("a2b1c1", "a", 0));
    TEST_DO(verify_optimized_multi("a1b2c1", "b", 1));
    TEST_DO(verify_optimized_multi("a1b1c2", "c", 2));
}

TEST_MAIN() { TEST_RUN_ALL(); }
