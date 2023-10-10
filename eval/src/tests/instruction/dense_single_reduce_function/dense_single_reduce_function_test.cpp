// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/instruction/dense_single_reduce_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;
using vespalib::make_string_short::fmt;

struct ReduceSpec {
    using LookFor = DenseSingleReduceFunction;
    size_t outer_size;
    size_t reduce_size;
    size_t inner_size;
    Aggr aggr;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQUAL(fun.outer_size(), outer_size);
        EXPECT_EQUAL(fun.reduce_size(), reduce_size);
        EXPECT_EQUAL(fun.inner_size(), inner_size);
        EXPECT_EQUAL(int(fun.aggr()), int(aggr));
    }
};

void verify_not_optimized(const vespalib::string &expr,
                          std::vector<CellType> with_cell_types = {CellType::DOUBLE})
{
    EvalFixture::verify<ReduceSpec>(expr, {}, CellTypeSpace(with_cell_types, 1));
}

void verify_optimized(const vespalib::string &expr, const ReduceSpec &spec,
                      std::vector<CellType> with_cell_types = CellTypeUtils::list_types())
{
    EvalFixture::verify<ReduceSpec>(expr, {spec}, CellTypeSpace(with_cell_types, 1));
}

void verify_optimized(const vespalib::string &expr, const ReduceSpec &spec1, const ReduceSpec &spec2,
                      std::vector<CellType> with_cell_types = CellTypeUtils::list_types())
{
    EvalFixture::verify<ReduceSpec>(expr, {spec1, spec2}, CellTypeSpace(with_cell_types, 1));
}

TEST("require that reduce to scalar is not optimized") {
    TEST_DO(verify_not_optimized("reduce(a10,sum,a)"));
    TEST_DO(verify_not_optimized("reduce(a10,sum)"));
}

TEST("require that sparse reduce is not optimized") {
    TEST_DO(verify_not_optimized("reduce(x2_1y2_1,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(x2_1y2_1,sum,y)"));
}

TEST("require that mixed reduce is not optimized") {
    TEST_DO(verify_not_optimized("reduce(x2_1y2_1z3,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(x2_1y2_1z3,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(x2_1y2_1z3,sum,z)"));
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
    TEST_DO(verify_optimized("reduce(a9b9c9d9,avg,a)", {1, 9, 729, Aggr::AVG}, {CellType::FLOAT}));
    TEST_DO(verify_optimized("reduce(a9b9c9d9,avg,b)", {9, 9, 81, Aggr::AVG}, {CellType::FLOAT}));
    TEST_DO(verify_optimized("reduce(a9b9c9d9,avg,c)", {81, 9, 9, Aggr::AVG}, {CellType::FLOAT}));
    TEST_DO(verify_optimized("reduce(a9b9c9d9,avg,d)", {729, 9, 1, Aggr::AVG}, {CellType::FLOAT}));
    TEST_DO(verify_optimized("reduce(a9b9c9d9,sum,c,d)", {81, 81, 1, Aggr::SUM}, {CellType::FLOAT}));
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

void verify_optimized_multi(const vespalib::string &arg, const vespalib::string &dim, size_t outer_size, size_t reduce_size, size_t inner_size) {
    for (Aggr aggr: Aggregator::list()) {
        if (aggr != Aggr::PROD) {
            auto expr = fmt("reduce(%s,%s,%s)", arg.c_str(), AggrNames::name_of(aggr)->c_str(), dim.c_str());
            TEST_DO(verify_optimized(expr, {outer_size, reduce_size, inner_size, aggr}));
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
