// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

auto full = GenSpec(-128).idx("x", 32).cells(CellType::INT8);
auto vx8 = GenSpec().seq(my_seq).idx("x", 8).cells(CellType::INT8);
auto vy8 = GenSpec().seq(my_seq).idx("y", 8).cells(CellType::INT8);
auto vxf = GenSpec().seq(my_seq).idx("x", 8).cells(CellType::FLOAT);
auto tmxy8 = GenSpec().seq(my_seq).idx("t", 1).idx("x", 3).idx("y", 4).cells(CellType::INT8);

void assert_expr(const GenSpec &spec, const vespalib::string &expr, bool optimized) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", spec);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EvalFixture test_fixture(test_factory, expr, param_repo, true);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    auto expect = EvalFixture::ref(expr, param_repo);
    EXPECT_EQ(fast_fixture.result(), expect);
    EXPECT_EQ(test_fixture.result(), expect);
    EXPECT_EQ(slow_fixture.result(), expect);
    EXPECT_EQ(fast_fixture.find_all<UnpackBitsFunction>().size(), optimized ? 1u : 0u);
    EXPECT_EQ(test_fixture.find_all<UnpackBitsFunction>().size(), optimized ? 1u : 0u);
    EXPECT_EQ(slow_fixture.find_all<UnpackBitsFunction>().size(), 0u);
}

void assert_impl(const GenSpec &spec, const vespalib::string &expr, bool optimized) {
    assert_expr(spec, expr, optimized);
    vespalib::string wrapped_expr("map_subspaces(a,f(a)(");
    wrapped_expr.append(expr);
    wrapped_expr.append("))");
    assert_expr(spec, wrapped_expr, optimized);
    assert_expr(spec.cpy().map("m", {"foo", "bar", "baz"}), wrapped_expr, optimized);
}

void assert_optimized(const GenSpec &spec, const vespalib::string &expr) {
    assert_impl(spec, expr, true);
}

void assert_not_optimized(const GenSpec &spec, const vespalib::string &expr) {
    assert_impl(spec, expr, false);
}

//-----------------------------------------------------------------------------

TEST(UnpackBitsTest, expression_can_be_optimized_with_big_bitorder) {
    assert_optimized(full, "tensor<int8>(x[256])(bit(a{x:(x/8)},7-x%8))");
    assert_optimized(vx8, "tensor<int8>(x[64])(bit(a{x:(x/8)},7-x%8))");
}

TEST(UnpackBitsTest, expression_can_be_optimized_with_small_bitorder) {
    assert_optimized(full, "tensor<int8>(x[256])(bit(a{x:(x/8)},x%8))");
    assert_optimized(vx8, "tensor<int8>(x[64])(bit(a{x:(x/8)},x%8))");
}

TEST(UnpackBitsTest, result_may_have_other_cell_types_than_int8) {
    assert_optimized(vx8, "tensor<bfloat16>(x[64])(bit(a{x:(x/8)},7-x%8))");
    assert_optimized(vx8, "tensor<float>(x[64])(bit(a{x:(x/8)},7-x%8))");
    assert_optimized(vx8, "tensor<double>(x[64])(bit(a{x:(x/8)},7-x%8))");

    assert_optimized(vx8, "tensor<bfloat16>(x[64])(bit(a{x:(x/8)},x%8))");
    assert_optimized(vx8, "tensor<float>(x[64])(bit(a{x:(x/8)},x%8))");
    assert_optimized(vx8, "tensor<double>(x[64])(bit(a{x:(x/8)},x%8))");
}

TEST(UnpackBitsTest, unpack_bits_can_have_multiple_dimensions) {
    assert_optimized(tmxy8, "tensor<int8>(t[1],x[3],y[32])(bit(a{t:(t),x:(x),y:(y/8)},7-y%8))");
    assert_optimized(tmxy8, "tensor<int8>(t[1],x[3],y[32])(bit(a{t:(t),x:(x),y:(y/8)},y%8))");
}

TEST(UnpackBitsTest, unpack_bits_can_rename_dimensions) {
    assert_optimized(tmxy8, "tensor<int8>(e[1],f[3],g[32])(bit(a{t:(e),x:(f),y:(g/8)},7-g%8))");
    assert_optimized(tmxy8, "tensor<int8>(e[1],f[3],g[32])(bit(a{t:(e),x:(f),y:(g/8)},g%8))");
}

//-----------------------------------------------------------------------------

TEST(UnpackBitsTest, source_must_be_int8) {
    assert_not_optimized(vxf, "tensor<int8>(x[64])(bit(a{x:(x/8)},7-x%8))");
}

TEST(UnpackBitsTest, dimension_sizes_must_be_appropriate) {
    assert_not_optimized(vx8, "tensor<int8>(x[60])(bit(a{x:(x/8)},7-x%8))");
    assert_not_optimized(vx8, "tensor<int8>(x[68])(bit(a{x:(x/8)},7-x%8))");
    assert_not_optimized(tmxy8, "tensor<int8>(e[1],f[2],g[32])(bit(a{t:(e),x:(f),y:(g/8)},7-g%8))");
    assert_not_optimized(tmxy8, "tensor<int8>(e[2],f[3],g[32])(bit(a{t:(e),x:(f),y:(g/8)},7-g%8))");
}

TEST(UnpackBitsTest, must_unpack_inner_dimension) {
    assert_not_optimized(tmxy8, "tensor<int8>(t[1],x[24],y[4])(bit(a{t:(t),x:(x/8),y:(y)},7-x%8))");
}

TEST(UnpackBitsTest, cannot_transpose_even_trivial_dimensions) {
    assert_not_optimized(tmxy8, "tensor<int8>(f[1],e[3],g[32])(bit(a{t:(f),x:(e),y:(g/8)},7-g%8))");
    assert_not_optimized(tmxy8, "tensor<int8>(f[1],e[3],g[32])(bit(a{t:(f),x:(e),y:(g/8)},g%8))");
}

TEST(UnpackBitsTest, outer_dimensions_must_be_dimension_index_directly) {
    assert_not_optimized(tmxy8, "tensor<int8>(t[1],x[3],y[32])(bit(a{t:0,x:(x),y:(y/8)},7-y%8))");
    assert_not_optimized(tmxy8, "tensor<int8>(t[1],x[3],y[32])(bit(a{t:(t),x:(x+1-1),y:(y/8)},7-y%8))");
}

TEST(UnpackBitsTest, similar_expressions_are_not_optimized) {
    assert_not_optimized(vx8, "tensor<int8>(x[64])(bit(a{x:(x*8)},7-x%8))");
    assert_not_optimized(vx8, "tensor<int8>(x[64])(bit(a{x:(x/9)},7-x%8))");
    assert_not_optimized(vx8, "tensor<int8>(x[64])(bit(a{x:(x/8)},8-x%8))");
    assert_not_optimized(vx8, "tensor<int8>(x[64])(bit(a{x:(x/8)},7+x%8))");
    assert_not_optimized(vx8, "tensor<int8>(x[64])(bit(a{x:(x/8)},7-x/8))");
    assert_not_optimized(vx8, "tensor<int8>(x[64])(bit(a{x:(x/8)},7-x%9))");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
