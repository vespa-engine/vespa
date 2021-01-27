// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/eval/instruction/sum_max_dot_product_function.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

struct MyVecSeq : Sequence {
    double bias;
    double operator[](size_t i) const override { return (i + bias); }
    MyVecSeq(double cellBias) : bias(cellBias) {}
};

//-----------------------------------------------------------------------------

vespalib::string main_expr = "reduce(reduce(reduce(a*b,sum,z),max,y),sum,x)";

void assert_optimized(const TensorSpec &a, const TensorSpec &b, size_t dp_size) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a);
    param_repo.add("b", b);
    EvalFixture slow_fixture(prod_factory, main_expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, main_expr, param_repo, true);
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(main_expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(main_expr, param_repo));
    auto info = fast_fixture.find_all<SumMaxDotProductFunction>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQUAL(info[0]->dp_size(), dp_size);
}

void assert_not_optimized(const TensorSpec &a, const TensorSpec &b, const vespalib::string &expr = main_expr) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a);
    param_repo.add("b", b);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fast_fixture.find_all<SumMaxDotProductFunction>();
    ASSERT_EQ(info.size(), 0u);
}

//-----------------------------------------------------------------------------

auto query = spec(float_cells({x({"0", "1", "2"}),z(5)}), MyVecSeq(0.5));
auto document = spec(float_cells({y({"0", "1", "2", "3", "4", "5"}),z(5)}), MyVecSeq(2.5));
auto empty_query = spec(float_cells({x({}),z(5)}), MyVecSeq(0.5));
auto empty_document = spec(float_cells({y({}),z(5)}), MyVecSeq(2.5));

TEST(SumMaxDotProduct, expressions_can_be_optimized)
{
    assert_optimized(query, document, 5);
    assert_optimized(document, query, 5);
    assert_optimized(empty_query, document, 5);
    assert_optimized(query, empty_document, 5);
    assert_optimized(empty_query, empty_document, 5);
}

TEST(SumMaxDotProduct, double_cells_are_not_optimized) {
    auto double_query = spec({x({"0", "1", "2"}),z(5)}, MyVecSeq(0.5));
    auto double_document = spec({y({"0", "1", "2", "3", "4", "5"}),z(5)}, MyVecSeq(2.5));
    assert_not_optimized(query, double_document);
    assert_not_optimized(double_query, document);
    assert_not_optimized(double_query, double_document);
}

TEST(SumMaxDotProduct, trivial_dot_product_is_not_optimized) {
    auto trivial_query = spec(float_cells({x({"0", "1", "2"}),z(1)}), MyVecSeq(0.5));
    auto trivial_document = spec(float_cells({y({"0", "1", "2", "3", "4", "5"}),z(1)}), MyVecSeq(2.5));
    assert_not_optimized(trivial_query, trivial_document);
}

TEST(SumMaxDotProduct, additional_dimensions_are_not_optimized) {
    auto extra_sparse_query = spec(float_cells({Domain("a", {"0"}),x({"0", "1", "2"}),z(5)}), MyVecSeq(0.5));
    auto extra_dense_query = spec(float_cells({Domain("a", 1),x({"0", "1", "2"}),z(5)}), MyVecSeq(0.5));
    auto extra_sparse_document = spec(float_cells({Domain("a", {"0"}),y({"0", "1", "2", "3", "4", "5"}),z(5)}), MyVecSeq(2.5));
    auto extra_dense_document = spec(float_cells({Domain("a", 1),y({"0", "1", "2", "3", "4", "5"}),z(5)}), MyVecSeq(2.5));
    vespalib::string extra_sum_expr = "reduce(reduce(reduce(a*b,sum,z),max,y),sum,a,x)";
    vespalib::string extra_max_expr = "reduce(reduce(reduce(a*b,sum,z),max,a,y),sum,x)";
    assert_not_optimized(extra_sparse_query, document);
    assert_not_optimized(extra_dense_query, document);
    assert_not_optimized(query, extra_sparse_document);
    assert_not_optimized(query, extra_dense_document);
    assert_not_optimized(extra_sparse_query, document, extra_sum_expr);
    assert_not_optimized(extra_dense_query, document, extra_sum_expr);
    assert_not_optimized(query, extra_sparse_document, extra_max_expr);
    assert_not_optimized(query, extra_dense_document, extra_max_expr);
}

TEST(SumMaxDotProduct, more_dense_variants_are_not_optimized) {
    auto dense_query = spec(float_cells({x(3),z(5)}), MyVecSeq(0.5));
    auto dense_document = spec(float_cells({y(5),z(5)}), MyVecSeq(2.5));
    assert_not_optimized(dense_query, document);
    assert_not_optimized(query, dense_document);
    assert_not_optimized(dense_query, dense_document);
}

TEST(SumMaxDotProduct, similar_expressions_are_not_optimized) {
    vespalib::string max_sum_expr = "reduce(reduce(reduce(a*b,sum,z),sum,y),max,x)";
    vespalib::string not_dp_expr1 = "reduce(reduce(reduce(a+b,sum,z),max,y),sum,x)";
    vespalib::string not_dp_expr2 = "reduce(reduce(reduce(a*b,min,z),max,y),sum,x)";
    vespalib::string sum_all_expr = "reduce(reduce(reduce(a*b,sum,z),max,y),sum)";
    assert_not_optimized(query, document, max_sum_expr);
    assert_not_optimized(query, document, not_dp_expr1);
    assert_not_optimized(query, document, not_dp_expr2);
    assert_not_optimized(query, document, sum_all_expr);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
