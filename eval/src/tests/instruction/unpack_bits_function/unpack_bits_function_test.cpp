// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/instruction/unpack_bits_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();
const ValueBuilderFactory &test_factory = SimpleValueBuilderFactory::get();

//-----------------------------------------------------------------------------

auto my_seq = Seq({-128, -43, 85, 127});

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("full", GenSpec(-128).idx("x", 256).cells(CellType::INT8))
        .add("vx8", GenSpec().seq(my_seq).idx("x", 8).cells(CellType::INT8))
        .add("vy8", GenSpec().seq(my_seq).idx("y", 8).cells(CellType::INT8))
        .add("vxf", GenSpec().seq(my_seq).idx("x", 8).cells(CellType::FLOAT));
}
EvalFixture::ParamRepo param_repo = make_params();

void assert_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EvalFixture test_fixture(test_factory, expr, param_repo, true);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    auto expect = EvalFixture::ref(expr, param_repo);
    EXPECT_EQ(fast_fixture.result(), expect);
    EXPECT_EQ(test_fixture.result(), expect);
    EXPECT_EQ(slow_fixture.result(), expect);
    EXPECT_EQ(fast_fixture.find_all<UnpackBitsFunction>().size(), 1u);
    EXPECT_EQ(test_fixture.find_all<UnpackBitsFunction>().size(), 1u);
    EXPECT_EQ(slow_fixture.find_all<UnpackBitsFunction>().size(), 0u);
}

void assert_not_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<UnpackBitsFunction>().size(), 0u);
}

//-----------------------------------------------------------------------------

TEST(UnpackBitsTest, expression_can_be_optimized_with_big_bitorder) {
    assert_optimized("tensor<int8>(x[2048])(bit(full{x:(x/8)},7-x%8))");
    assert_optimized("tensor<int8>(x[64])(bit(vx8{x:(x/8)},7-x%8))");
}

TEST(UnpackBitsTest, expression_can_be_optimized_with_small_bitorder) {
    assert_optimized("tensor<int8>(x[2048])(bit(full{x:(x/8)},x%8))");
    assert_optimized("tensor<int8>(x[64])(bit(vx8{x:(x/8)},x%8))");
}

TEST(UnpackBitsTest, unpack_bits_can_rename_dimension) {
    assert_optimized("tensor<int8>(x[64])(bit(vy8{y:(x/8)},7-x%8))");
    assert_optimized("tensor<int8>(x[64])(bit(vy8{y:(x/8)},x%8))");
}

TEST(UnpackBitsTest, result_may_have_other_cell_types_than_int8) {
    assert_optimized("tensor<bfloat16>(x[64])(bit(vx8{x:(x/8)},7-x%8))");
    assert_optimized("tensor<float>(x[64])(bit(vx8{x:(x/8)},7-x%8))");
    assert_optimized("tensor<double>(x[64])(bit(vx8{x:(x/8)},7-x%8))");

    assert_optimized("tensor<bfloat16>(x[64])(bit(vx8{x:(x/8)},x%8))");
    assert_optimized("tensor<float>(x[64])(bit(vx8{x:(x/8)},x%8))");
    assert_optimized("tensor<double>(x[64])(bit(vx8{x:(x/8)},x%8))");
}

//-----------------------------------------------------------------------------

TEST(UnpackBitsTest, source_must_be_int8) {
    assert_not_optimized("tensor<int8>(x[64])(bit(vxf{x:(x/8)},7-x%8))");
}

TEST(UnpackBitsTest, dimension_sizes_must_be_appropriate) {
    assert_not_optimized("tensor<int8>(x[60])(bit(vx8{x:(x/8)},7-x%8))");
    assert_not_optimized("tensor<int8>(x[68])(bit(vx8{x:(x/8)},7-x%8))");
}

TEST(UnpackBitsTest, similar_expressions_are_not_optimized) {
    assert_not_optimized("tensor<int8>(x[64])(bit(vx8{x:(x*8)},7-x%8))");
    assert_not_optimized("tensor<int8>(x[64])(bit(vx8{x:(x/9)},7-x%8))");
    assert_not_optimized("tensor<int8>(x[64])(bit(vx8{x:(x/8)},8-x%8))");
    assert_not_optimized("tensor<int8>(x[64])(bit(vx8{x:(x/8)},7+x%8))");
    assert_not_optimized("tensor<int8>(x[64])(bit(vx8{x:(x/8)},7-x/8))");
    assert_not_optimized("tensor<int8>(x[64])(bit(vx8{x:(x/8)},7-x%9))");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
