// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/instruction/simple_join_count.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;

//-----------------------------------------------------------------------------

struct FunInfo {
    using LookFor = SimpleJoinCount;
    uint64_t expected_dense_factor;
    FunInfo(uint64_t expected_dense_factor_in)
      : expected_dense_factor(expected_dense_factor_in) {}
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQ(fun.dense_factor(), expected_dense_factor);
    }
};

void verify_optimized_cell_types(const vespalib::string &expr, size_t expected_dense_factor = 1) {
    CellTypeSpace types(CellTypeUtils::list_types(), 2);
    EvalFixture::verify<FunInfo>(expr, {FunInfo(expected_dense_factor)}, types);
}

void verify_optimized(const vespalib::string &expr, size_t expected_dense_factor = 1) {
    CellTypeSpace just_float({CellType::FLOAT}, 2);
    EvalFixture::verify<FunInfo>(expr, {FunInfo(expected_dense_factor)}, just_float);
}

void verify_not_optimized(const vespalib::string &expr) {
    CellTypeSpace just_float({CellType::FLOAT}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_float);
}

//-----------------------------------------------------------------------------

TEST(SimpleJoinCount, expression_can_be_optimized) {
    verify_optimized_cell_types("reduce(x5_2*x5_1,count)");
    verify_optimized_cell_types("reduce(x5_2y3z4*x5_1z4a1,count)", 12);
}

TEST(SimpleJoinCount, join_operation_does_not_matter) {
    verify_optimized("reduce(x5_2+x5_1,count)");
    verify_optimized("reduce(x5_2-x5_1,count)");
    verify_optimized("reduce(x5_2/x5_1,count)");
}

TEST(SimpleJoinCount, parameters_must_have_full_mapped_singledim_overlap) {
    verify_not_optimized("reduce(x5_2y5_2*x5_1y5_2,count)");
    verify_not_optimized("reduce(x5_2*y5_2,count)");
    verify_not_optimized("reduce(x5_2y5_2*x5_1z5_2,count)");
    verify_not_optimized("reduce(x5_2*y5,count)");
    verify_not_optimized("reduce(x5*y5,count)");
}

TEST(SimpleJoinCount, similar_expressions_are_not_optimized) {
    verify_not_optimized("reduce(x5_2y3z4*x5_1z4a1,count,x)");
    verify_not_optimized("reduce(x5_2y3z4*x5_1z4a1,count,x,y,z)");
    verify_not_optimized("reduce(x5_2y3*x5_1,sum)");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
