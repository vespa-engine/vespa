// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/instruction/dense_single_reduce_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

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
        EXPECT_EQ(fun.outer_size(), outer_size);
        EXPECT_EQ(fun.reduce_size(), reduce_size);
        EXPECT_EQ(fun.inner_size(), inner_size);
        EXPECT_EQ(int(fun.aggr()), int(aggr));
    }
};

void verify_not_optimized(const std::string &expr,
                          std::vector<CellType> with_cell_types = {CellType::DOUBLE})
{
    SCOPED_TRACE(expr);
    EvalFixture::verify<ReduceSpec>(expr, {}, CellTypeSpace(with_cell_types, 1));
}

void verify_optimized(const std::string &expr, const ReduceSpec &spec,
                      std::vector<CellType> with_cell_types = CellTypeUtils::list_types())
{
    SCOPED_TRACE(expr);
    EvalFixture::verify<ReduceSpec>(expr, {spec}, CellTypeSpace(with_cell_types, 1));
}

void verify_optimized(const std::string &expr, const ReduceSpec &spec1, const ReduceSpec &spec2,
                      std::vector<CellType> with_cell_types = CellTypeUtils::list_types())
{
    SCOPED_TRACE(expr);
    EvalFixture::verify<ReduceSpec>(expr, {spec1, spec2}, CellTypeSpace(with_cell_types, 1));
}

TEST(DenseSingleReduceFunctionTest, require_that_reduce_to_scalar_is_not_optimized)
{
    verify_not_optimized("reduce(a10,sum,a)");
    verify_not_optimized("reduce(a10,sum)");
}

TEST(DenseSingleReduceFunctionTest, require_that_sparse_reduce_is_not_optimized)
{
    verify_not_optimized("reduce(x2_1y2_1,sum,x)");
    verify_not_optimized("reduce(x2_1y2_1,sum,y)");
}

TEST(DenseSingleReduceFunctionTest, require_that_mixed_reduce_is_not_optimized)
{
    verify_not_optimized("reduce(x2_1y2_1z3,sum,x)");
    verify_not_optimized("reduce(x2_1y2_1z3,sum,y)");
    verify_not_optimized("reduce(x2_1y2_1z3,sum,z)");
}

TEST(DenseSingleReduceFunctionTest, require_that_reducing_trivial_dimensions_is_not_optimized)
{
    verify_not_optimized("reduce(a1b1c1,avg,c)");
    verify_not_optimized("reduce(a1b1c1,count,c)");
    verify_not_optimized("reduce(a1b1c1,prod,c)");
    verify_not_optimized("reduce(a1b1c1,sum,c)");
    verify_not_optimized("reduce(a1b1c1,max,c)");
    verify_not_optimized("reduce(a1b1c1,median,c)");
    verify_not_optimized("reduce(a1b1c1,min,c)");
}

TEST(DenseSingleReduceFunctionTest, require_that_atleast_8_dense_single_reduce_works)
{
    verify_optimized("reduce(a9b9c9d9,avg,a)", {1, 9, 729, Aggr::AVG}, {CellType::FLOAT});
    verify_optimized("reduce(a9b9c9d9,avg,b)", {9, 9, 81, Aggr::AVG}, {CellType::FLOAT});
    verify_optimized("reduce(a9b9c9d9,avg,c)", {81, 9, 9, Aggr::AVG}, {CellType::FLOAT});
    verify_optimized("reduce(a9b9c9d9,avg,d)", {729, 9, 1, Aggr::AVG}, {CellType::FLOAT});
    verify_optimized("reduce(a9b9c9d9,sum,c,d)", {81, 81, 1, Aggr::SUM}, {CellType::FLOAT});
}

TEST(DenseSingleReduceFunctionTest, require_that_simple_aggregators_can_be_decomposed_into_multiple_reduce_operations)
{
    verify_optimized("reduce(a2b3c4d5,sum,a,c)", {3, 4, 5, Aggr::SUM}, {1, 2, 60, Aggr::SUM});
    verify_optimized("reduce(a2b3c4d5,min,a,c)", {3, 4, 5, Aggr::MIN}, {1, 2, 60, Aggr::MIN});
    verify_optimized("reduce(a2b3c4d5,max,a,c)", {3, 4, 5, Aggr::MAX}, {1, 2, 60, Aggr::MAX});
}

TEST(DenseSingleReduceFunctionTest, require_that_reduce_dimensions_can_be_listed_in_reverse_order)
{
    verify_optimized("reduce(a2b3c4d5,sum,c,a)", {3, 4, 5, Aggr::SUM}, {1, 2, 60, Aggr::SUM});
    verify_optimized("reduce(a2b3c4d5,min,c,a)", {3, 4, 5, Aggr::MIN}, {1, 2, 60, Aggr::MIN});
    verify_optimized("reduce(a2b3c4d5,max,c,a)", {3, 4, 5, Aggr::MAX}, {1, 2, 60, Aggr::MAX});
}

TEST(DenseSingleReduceFunctionTest, require_that_non_simple_aggregators_cannot_be_decomposed_into_multiple_reduce_operations)
{
    verify_not_optimized("reduce(a2b3c4d5,avg,a,c)");
    verify_not_optimized("reduce(a2b3c4d5,count,a,c)");
    verify_not_optimized("reduce(a2b3c4d5,median,a,c)");
}

void verify_optimized_multi(const std::string &arg, const std::string &dim, size_t outer_size, size_t reduce_size, size_t inner_size) {
    SCOPED_TRACE(fmt("verify_optimized_multi(\"%s\",\"%s\",...)", arg.c_str(), dim.c_str()));
    for (Aggr aggr: Aggregator::list()) {
        if (aggr != Aggr::PROD) {
            auto expr = fmt("reduce(%s,%s,%s)", arg.c_str(), AggrNames::name_of(aggr)->c_str(), dim.c_str());
            verify_optimized(expr, {outer_size, reduce_size, inner_size, aggr});
        }
    }
}

TEST(DenseSingleReduceFunctionTest, require_that_normal_dense_single_reduce_works)
{
    verify_optimized_multi("a2b3c4d5", "a", 1, 2, 60);
    verify_optimized_multi("a2b3c4d5", "b", 2, 3, 20);
    verify_optimized_multi("a2b3c4d5", "c", 6, 4, 5);
    verify_optimized_multi("a2b3c4d5", "d", 24, 5, 1);
}

TEST(DenseSingleReduceFunctionTest, require_that_dimension_combined_dense_single_reduce_works)
{
    verify_optimized_multi("a2b3c4d5", "a,b", 1, 6, 20);
    verify_optimized_multi("a2b3c4d5", "b,c", 2, 12, 5);
    verify_optimized_multi("a2b3c4d5", "c,d", 6, 20, 1);
}

TEST(DenseSingleReduceFunctionTest, require_that_minimal_dense_single_reduce_works)
{
    verify_optimized_multi("a2b1c1", "a", 1, 2, 1);
    verify_optimized_multi("a1b2c1", "b", 1, 2, 1);
    verify_optimized_multi("a1b1c2", "c", 1, 2, 1);
}

TEST(DenseSingleReduceFunctionTest, require_that_trivial_dimensions_can_be_trivially_reduced)
{
    verify_optimized_multi("a2b1c1", "a,b", 1, 2, 1);
    verify_optimized_multi("a2b1c1", "a,c", 1, 2, 1);
    verify_optimized_multi("a1b2c1", "b,a", 1, 2, 1);
    verify_optimized_multi("a1b2c1", "b,c", 1, 2, 1);
    verify_optimized_multi("a1b1c2", "c,a", 1, 2, 1);
    verify_optimized_multi("a1b1c2", "c,b", 1, 2, 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
