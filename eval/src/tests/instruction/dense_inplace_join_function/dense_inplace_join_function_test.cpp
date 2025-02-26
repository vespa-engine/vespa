// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

GenSpec::seq_t glb = [] (size_t) noexcept {
    static double seq_value = 0.0;
    seq_value += 1.0;
    return seq_value;
};

EvalFixture::ParamRepo make_params() {
    EvalFixture::ParamRepo repo;
    for (std::string param : {
            "x5$1", "x5$2", "x5$3",
            "x5y3$1", "x5y3$2",
            "@x5$1", "@x5$2", "@x5$3",
            "@x5y3$1", "@x5y3$2",
            "@x3_1$1", "@x3_1$2"
        })
    {
        repo.add(param, CellType::DOUBLE, glb);
        repo.add(param + "_f", CellType::FLOAT, glb);
    }
    repo.add_mutable("mut_dbl_A", GenSpec(1.5));
    repo.add_mutable("mut_dbl_B", GenSpec(2.5));
    return repo;
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const std::string &expr, size_t param_idx) {
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    for (size_t i = 0; i < fixture.num_params(); ++i) {
        SCOPED_TRACE(vespalib::make_string("param %zu", i));
        if (i == param_idx) {
            EXPECT_EQ(fixture.param_value(i).cells().data, fixture.result_value().cells().data);
        } else {
            EXPECT_NE(fixture.param_value(i).cells().data, fixture.result_value().cells().data);
        }
    }
}

void verify_p0_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    verify_optimized(expr, 0);
}

void verify_p1_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    verify_optimized(expr, 1);
}

void verify_p2_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    verify_optimized(expr, 2);
}

void verify_not_optimized(const std::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    for (size_t i = 0; i < fixture.num_params(); ++i) {
        EXPECT_NE(fixture.param_value(i).cells().data, fixture.result_value().cells().data);
    }
}

TEST(DenseInplaceJoinFunctionTest, require_that_mutable_dense_concrete_tensors_are_optimized)
{
    verify_p1_optimized("@x5$1-@x5$2");
    verify_p0_optimized("@x5$1-x5$2");
    verify_p1_optimized("x5$1-@x5$2");
    verify_p1_optimized("@x5y3$1-@x5y3$2");
    verify_p0_optimized("@x5y3$1-x5y3$2");
    verify_p1_optimized("x5y3$1-@x5y3$2");
}

TEST(DenseInplaceJoinFunctionTest, require_that_self_join_operations_can_be_optimized)
{
    verify_p0_optimized("@x5$1+@x5$1");
}

TEST(DenseInplaceJoinFunctionTest, require_that_join_tensor_with_scalar_operations_are_optimized)
{
    verify_p0_optimized("@x5$1-mut_dbl_B");
    verify_p1_optimized("mut_dbl_A-@x5$2");
}

TEST(DenseInplaceJoinFunctionTest, require_that_join_with_different_tensor_shapes_are_optimized)
{
    verify_p1_optimized("@x5$1*@x5y3$2");
}

TEST(DenseInplaceJoinFunctionTest, require_that_inplace_join_operations_can_be_chained)
{
    verify_p2_optimized("@x5$1+(@x5$2+@x5$3)");
    verify_p0_optimized("(@x5$1+x5$2)+x5$3");
    verify_p1_optimized("x5$1+(@x5$2+x5$3)");
    verify_p2_optimized("x5$1+(x5$2+@x5$3)");
}

TEST(DenseInplaceJoinFunctionTest, require_that_non_mutable_tensors_are_not_optimized)
{
    verify_not_optimized("x5$1+x5$2");
}

TEST(DenseInplaceJoinFunctionTest, require_that_scalar_values_are_not_optimized)
{
    verify_not_optimized("mut_dbl_A+mut_dbl_B");
    verify_not_optimized("mut_dbl_A+5");
    verify_not_optimized("5+mut_dbl_B");
}

TEST(DenseInplaceJoinFunctionTest, require_that_mapped_tensors_are_not_optimized)
{
    verify_not_optimized("@x3_1$1+@x3_1$2");
}

TEST(DenseInplaceJoinFunctionTest, require_that_optimization_works_with_float_cells)
{
    verify_p1_optimized("@x5$1_f-@x5$2_f");
}

TEST(DenseInplaceJoinFunctionTest, require_that_overwritten_value_must_have_same_cell_type_as_result)
{
    verify_p0_optimized("@x5$1-@x5$2_f");
    verify_p1_optimized("@x5$2_f-@x5$1");
    verify_not_optimized("x5$1-@x5$2_f");
    verify_not_optimized("@x5$2_f-x5$1");
}

GTEST_MAIN_RUN_ALL_TESTS()
