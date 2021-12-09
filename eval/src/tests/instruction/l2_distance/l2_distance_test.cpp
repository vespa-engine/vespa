// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/l2_distance.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

//-----------------------------------------------------------------------------

void verify(const TensorSpec &a, const TensorSpec &b, const vespalib::string &expr, bool optimized = true) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a).add("b", b);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<L2Distance>().size(), optimized ? 1 : 0);
}

void verify_cell_types(GenSpec a, GenSpec b, const vespalib::string &expr, bool optimized = true) {
    for (CellType act : CellTypeUtils::list_types()) {
        for (CellType bct : CellTypeUtils::list_types()) {
            if (optimized && (act == bct) && (act != CellType::BFLOAT16)) {
                verify(a.cpy().cells(act), b.cpy().cells(bct), expr, true);
            } else {
                verify(a.cpy().cells(act), b.cpy().cells(bct), expr, false);
            }
        }
    }
}

//-----------------------------------------------------------------------------

GenSpec gen(const vespalib::string &desc, int bias) {
    return GenSpec::from_desc(desc).cells(CellType::FLOAT).seq(N(bias));
}

//-----------------------------------------------------------------------------

vespalib::string sq_l2 = "reduce((a-b)^2,sum)";
vespalib::string alt_sq_l2 = "reduce(map((a-b),f(x)(x*x)),sum)";

//-----------------------------------------------------------------------------

TEST(L2DistanceTest, squared_l2_distance_can_be_optimized) {
    verify_cell_types(gen("x5", 3), gen("x5", 7), sq_l2);
    verify_cell_types(gen("x5", 3), gen("x5", 7), alt_sq_l2);
}

TEST(L2DistanceTest, trivial_dimensions_are_ignored) {
    verify(gen("x5y1", 3), gen("x5", 7), sq_l2);
    verify(gen("x5", 3), gen("x5y1", 7), sq_l2);
}

TEST(L2DistanceTest, multiple_dimensions_can_be_used) {
    verify(gen("x5y3", 3), gen("x5y3", 7), sq_l2);
}

//-----------------------------------------------------------------------------

TEST(L2DistanceTest, inputs_must_be_dense) {
    verify(gen("x5_1", 3), gen("x5_1", 7), sq_l2, false);
    verify(gen("x5_1y3", 3), gen("x5_1y3", 7), sq_l2, false);
    verify(gen("x5", 3), GenSpec(7), sq_l2, false);
    verify(GenSpec(3), gen("x5", 7), sq_l2, false);
}

TEST(L2DistanceTest, result_must_be_double) {
    verify(gen("x5y1", 3), gen("x5y1", 7), "reduce((a-b)^2,sum,x)", false);
    verify(gen("x5y1_1", 3), gen("x5y1_1", 7), "reduce((a-b)^2,sum,x)", false);
}

TEST(L2DistanceTest, dimensions_must_match) {
    verify(gen("x5y3", 3), gen("x5", 7), sq_l2, false);
    verify(gen("x5", 3), gen("x5y3", 7), sq_l2, false);
}

TEST(L2DistanceTest, similar_expressions_are_not_optimized) {
    verify(gen("x5", 3), gen("x5", 7), "reduce((a-b)^2,prod)", false);
    verify(gen("x5", 3), gen("x5", 7), "reduce((a-b)^3,sum)", false);
    verify(gen("x5", 3), gen("x5", 7), "reduce((a+b)^2,sum)", false);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
