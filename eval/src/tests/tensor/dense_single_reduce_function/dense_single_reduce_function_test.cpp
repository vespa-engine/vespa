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
        .add_dense({{"a", 9}, {"b", 9}, {"c", 9}, {"d", 9}})
        .add_cube("a", 2, "b", 1, "c", 1)
        .add_cube("a", 1, "b", 2, "c", 1)
        .add_cube("a", 1, "b", 1, "c", 2)
        .add_cube("a", 1, "b", 1, "c", 1)
        .add_vector("a", 10)
        .add("xy_mapped", spec({x({"a", "b"}),y({"x", "y"})}, N()))
        .add("xyz_mixed", spec({x({"a", "b"}),y({"x", "y"}),z(3)}, N()));
}
EvalFixture::ParamRepo param_repo = make_params();

struct ReduceSpec {
    size_t outer_size;
    size_t reduce_size;
    size_t inner_size;
    Aggr aggr;
};

void verify_optimized_impl(const vespalib::string &expr, const std::vector<ReduceSpec> &spec_list) {
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSingleReduceFunction>();
    ASSERT_EQUAL(info.size(), spec_list.size());
    for (size_t i = 0; i < spec_list.size(); ++i) {
        EXPECT_TRUE(info[i]->result_is_mutable());
        EXPECT_EQUAL(info[i]->outer_size(), spec_list[i].outer_size);
        EXPECT_EQUAL(info[i]->reduce_size(), spec_list[i].reduce_size);
        EXPECT_EQUAL(info[i]->inner_size(), spec_list[i].inner_size);
        EXPECT_EQUAL(int(info[i]->aggr()), int(spec_list[i].aggr));
    }
}

void verify_optimized(const vespalib::string &expr, const ReduceSpec &spec) {
    verify_optimized_impl(expr, {spec});
}

void verify_optimized(const vespalib::string &expr, const ReduceSpec &spec1, const ReduceSpec &spec2) {
    verify_optimized_impl(expr, {spec1, spec2});
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSingleReduceFunction>();
    EXPECT_TRUE(info.empty());
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

TEST("require that reducing trivial dimensions is not optimized") {
    TEST_DO(verify_not_optimized("reduce(a1b1c1,avg,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,count,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,prod,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,sum,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,max,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,median,c)"));
    TEST_DO(verify_not_optimized("reduce(a1b1c1,min,c)"));
}

TEST("require that atleast_8 dense single reduce works") {
    TEST_DO(verify_optimized("reduce(a9b9c9d9,avg,a)", {1, 9, 729, Aggr::AVG}));
    TEST_DO(verify_optimized("reduce(a9b9c9d9,avg,b)", {9, 9, 81, Aggr::AVG}));
    TEST_DO(verify_optimized("reduce(a9b9c9d9,avg,c)", {81, 9, 9, Aggr::AVG}));
    TEST_DO(verify_optimized("reduce(a9b9c9d9,avg,d)", {729, 9, 1, Aggr::AVG}));
    TEST_DO(verify_optimized("reduce(a9b9c9d9,sum,c,d)", {81, 81, 1, Aggr::SUM}));
}

TEST("require that simple aggregators can be decomposed into multiple reduce operations") {
    TEST_DO(verify_optimized("reduce(a2b3c4d5,sum,a,c)", {3, 4, 5, Aggr::SUM}, {1, 2, 60, Aggr::SUM}));
    TEST_DO(verify_optimized("reduce(a2b3c4d5,min,a,c)", {3, 4, 5, Aggr::MIN}, {1, 2, 60, Aggr::MIN}));
    TEST_DO(verify_optimized("reduce(a2b3c4d5,max,a,c)", {3, 4, 5, Aggr::MAX}, {1, 2, 60, Aggr::MAX}));
}

TEST("require that reduce dimensions can be listed in reverse order") {
    TEST_DO(verify_optimized("reduce(a2b3c4d5,sum,c,a)", {3, 4, 5, Aggr::SUM}, {1, 2, 60, Aggr::SUM}));
    TEST_DO(verify_optimized("reduce(a2b3c4d5,min,c,a)", {3, 4, 5, Aggr::MIN}, {1, 2, 60, Aggr::MIN}));
    TEST_DO(verify_optimized("reduce(a2b3c4d5,max,c,a)", {3, 4, 5, Aggr::MAX}, {1, 2, 60, Aggr::MAX}));
}

TEST("require that non-simple aggregators cannot be decomposed into multiple reduce operations") {
    TEST_DO(verify_not_optimized("reduce(a2b3c4d5,avg,a,c)"));
    TEST_DO(verify_not_optimized("reduce(a2b3c4d5,count,a,c)"));
    TEST_DO(verify_not_optimized("reduce(a2b3c4d5,median,a,c)"));
}

vespalib::string make_expr(const vespalib::string &arg, const vespalib::string &dim, bool float_cells, Aggr aggr) {
    return make_string("reduce(%s%s,%s,%s)", arg.c_str(), float_cells ? "f" : "", AggrNames::name_of(aggr)->c_str(), dim.c_str());
}

void verify_optimized_multi(const vespalib::string &arg, const vespalib::string &dim, size_t outer_size, size_t reduce_size, size_t inner_size) {
    for (bool float_cells: {false, true}) {
        for (Aggr aggr: Aggregator::list()) {
            if (aggr != Aggr::PROD) {
                auto expr = make_expr(arg, dim, float_cells, aggr);
                TEST_DO(verify_optimized(expr, {outer_size, reduce_size, inner_size, aggr}));
            }
        }
    }
}

TEST("require that normal dense single reduce works") {
    TEST_DO(verify_optimized_multi("a2b3c4d5", "a", 1, 2, 60));
    TEST_DO(verify_optimized_multi("a2b3c4d5", "b", 2, 3, 20));
    TEST_DO(verify_optimized_multi("a2b3c4d5", "c", 6, 4, 5));
    TEST_DO(verify_optimized_multi("a2b3c4d5", "d", 24, 5, 1));
}

TEST("require that dimension-combined dense single reduce works") {
    TEST_DO(verify_optimized_multi("a2b3c4d5", "a,b", 1, 6, 20));
    TEST_DO(verify_optimized_multi("a2b3c4d5", "b,c", 2, 12, 5));
    TEST_DO(verify_optimized_multi("a2b3c4d5", "c,d", 6, 20, 1));
}

TEST("require that minimal dense single reduce works") {
    TEST_DO(verify_optimized_multi("a2b1c1", "a", 1, 2, 1));
    TEST_DO(verify_optimized_multi("a1b2c1", "b", 1, 2, 1));
    TEST_DO(verify_optimized_multi("a1b1c2", "c", 1, 2, 1));
}

TEST("require that trivial dimensions can be trivially reduced") {
    TEST_DO(verify_optimized_multi("a2b1c1", "a,b", 1, 2, 1));
    TEST_DO(verify_optimized_multi("a2b1c1", "a,c", 1, 2, 1));
    TEST_DO(verify_optimized_multi("a1b2c1", "b,a", 1, 2, 1));
    TEST_DO(verify_optimized_multi("a1b2c1", "b,c", 1, 2, 1));
    TEST_DO(verify_optimized_multi("a1b1c2", "c,a", 1, 2, 1));
    TEST_DO(verify_optimized_multi("a1b1c2", "c,b", 1, 2, 1));
}

TEST_MAIN() { TEST_RUN_ALL(); }
