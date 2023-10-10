// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/best_similarity_function.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

//-----------------------------------------------------------------------------

void verify_impl(const TensorSpec &a, const TensorSpec &b, const vespalib::string &expr, bool optimized) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a).add("b", b);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<BestSimilarityFunction>().size(), optimized ? 1 : 0);
}

void verify(const TensorSpec &a, const TensorSpec &b, const vespalib::string &expr, bool optimized = true) {
    verify_impl(a, b, expr, optimized);
    verify_impl(b, a, expr, optimized);
}

//-----------------------------------------------------------------------------

GenSpec gen_double(const vespalib::string &desc, int bias) {
    return GenSpec::from_desc(desc).cells(CellType::DOUBLE).seq(N(bias));
}

GenSpec gen_float(const vespalib::string &desc, int bias) {
    return GenSpec::from_desc(desc).cells(CellType::FLOAT).seq(N(bias));
}

GenSpec gen_int8(const vespalib::string &desc, int bias) {
    return GenSpec::from_desc(desc).cells(CellType::INT8).seq(N(bias));
}

vespalib::string max_sim = "reduce(reduce(a*b,sum,d),max,b)";
vespalib::string min_hamming = "reduce(reduce(hamming(a,b),sum,d),min,b)";

//-----------------------------------------------------------------------------

TEST(BestSimilarityFunctionTest, result_is_mutable) {
    tensor_function::Inject child(ValueType::double_type(), 0);
    BestSimilarityFunction node(ValueType::double_type(), child, child, nullptr, 1);
    EXPECT_TRUE(node.result_is_mutable());
}

TEST(BestSimilarityFunctionTest, max_sim_can_be_optimized) {
    verify(gen_float("A3_2B3d8", 3), gen_float("b5d8", 7), max_sim);
    verify(gen_float("A3_2B3d8", 3), gen_float("b5_2d8", 7), max_sim);
}

TEST(BestSimilarityFunctionTest, min_hamming_can_be_optimized) {
    verify(gen_int8("A3_2B3d8", 3), gen_int8("b5d8", 7), min_hamming);
    verify(gen_int8("A3_2B3d8", 3), gen_int8("b5_2d8", 7), min_hamming);
}

TEST(BestSimilarityFunctionTest, result_can_be_sparse) {
    verify(gen_float("A3_2d8", 3), gen_float("b5d8", 7), max_sim);
    verify(gen_int8("A3_2d8", 3), gen_int8("b5_2d8", 7), min_hamming);
}

TEST(BestSimilarityFunctionTest, result_can_be_dense) {
    verify(gen_float("B3d8", 3), gen_float("b5d8", 7), max_sim);
    verify(gen_int8("B3d8", 3), gen_int8("b5_2d8", 7), min_hamming);
}

TEST(BestSimilarityFunctionTest, result_can_be_double) {
    verify(gen_float("d8", 3), gen_float("b5d8", 7), max_sim);
    verify(gen_int8("d8", 3), gen_int8("b5_2d8", 7), min_hamming);
}

TEST(BestSimilarityFunctionTest, primary_dimensions_can_be_trivial) {
    verify(gen_float("d1", 3), gen_float("b1d1", 7), max_sim);
    verify(gen_int8("d1", 3), gen_int8("b1d1", 7), min_hamming);
}

TEST(BestSimilarityFunctionTest, extra_trivial_dimensions_are_allowed) {
    verify(gen_float("A1a1d8x1z1", 3), gen_float("a1b5c1d8x1y1", 7), max_sim);
}

TEST(BestSimilarityFunctionTest, allow_full_reduce_for_outer_dimension) {
    vespalib::string my_max_sim = "reduce(reduce(a*b,sum,d),max)";
    vespalib::string my_min_hamming = "reduce(reduce(hamming(a,b),sum,d),min)";
    verify(gen_float("d8", 3), gen_float("b5d8", 7), my_max_sim);
    verify(gen_int8("d8", 3), gen_int8("b5_2d8", 7), my_min_hamming);
}

vespalib::string inv_max_sim = "reduce(reduce(a*b,sum,b),max,d)";

TEST(BestSimilarityFunctionTest, dimensions_can_be_inverted_if_best_dimension_is_sparse) {
    verify(gen_float("b8", 3), gen_float("b8d5_2", 7), inv_max_sim);
}

//-----------------------------------------------------------------------------

TEST(BestSimilarityFunctionTest, cell_type_must_match_operation) {
    verify(gen_double("d8", 3), gen_double("b5d8", 7), max_sim, false);
    verify(gen_float("d8", 3), gen_float("b5_2d8", 7), min_hamming, false);
}

TEST(BestSimilarityFunctionTest, similarity_must_use_1d_vector) {
    vespalib::string max_sim_2d_dist = "reduce(reduce(a*b,sum,d,e),max,b)";
    verify(gen_float("d8_1", 3), gen_float("b5d8_1", 7), max_sim, false);
    verify(gen_float("d8e1", 3), gen_float("b5d8e1", 7), max_sim_2d_dist, false);
}

TEST(BestSimilarityFunctionTest, similarity_dimension_must_be_inner) {
    verify(gen_float("d8e3", 3), gen_float("b5d8", 7), max_sim, false);
    verify(gen_float("b8", 3), gen_float("b8d5", 7), inv_max_sim, false);
}

TEST(BestSimilarityFunctionTest, alternatives_must_use_a_single_dimension) {
    vespalib::string max_sim_2d_best = "reduce(reduce(a*b,sum,d),max,a,b)";
    verify(gen_float("d8", 3), gen_float("a1b5d8", 7), max_sim_2d_best, false);
}

TEST(BestSimilarityFunctionTest, alternatives_dimension_can_not_be_common) {
    verify(gen_float("b5d8", 3), gen_float("b5d8", 7), max_sim, false);
}

TEST(BestSimilarityFunctionTest, extra_common_nontrivial_dimensions_not_allowed) {
    verify(gen_float("a3d8", 3), gen_float("a3b5d8", 7), max_sim, false);
    verify(gen_float("a3_2d8", 3), gen_float("a3_2b5d8", 7), max_sim, false);
}

TEST(BestSimilarityFunctionTest, secondary_tensor_must_not_contain_extra_nontrivial_dimensions) {
    verify(gen_float("d8", 3), gen_float("a2b5d8", 7), max_sim, false);
    verify(gen_float("d8", 3), gen_float("a2_1b5d8", 7), max_sim, false);
}

//-----------------------------------------------------------------------------

TEST(BestSimilarityFunctionTest, similar_expressions_are_not_optimized) {
    vespalib::string other_join = "reduce(reduce(a+b,sum,d),max,b)";
    vespalib::string other_reduce = "reduce(reduce(a*b,min,d),max,b)";
    vespalib::string mismatch_best_sim = "reduce(reduce(a*b,sum,d),min,b)";
    vespalib::string mismatch_best_hamming = "reduce(reduce(hamming(a,b),sum,d),max,b)";
    verify(gen_float("d8", 3), gen_float("b5d8", 7), other_join, false);
    verify(gen_float("d8", 3), gen_float("b5d8", 7), other_reduce, false);
    verify(gen_float("d8", 3), gen_float("b5d8", 7), mismatch_best_sim, false);
    verify(gen_int8("d8", 3), gen_int8("b5d8", 7), mismatch_best_hamming, false);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
