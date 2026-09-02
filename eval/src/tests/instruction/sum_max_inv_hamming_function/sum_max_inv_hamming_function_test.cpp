// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/sum_max_inv_hamming_function.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <cmath>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory& prod_factory = FastValueBuilderFactory::get();

std::string main_expr = "reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,y),sum,x)";
std::string alt_expr = "reduce(reduce(1/(reduce(hamming(a,b),sum,z)+1),max,y),sum,x)";
std::string chunked_expr = "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,y),sum,x),max,c)";
std::string chunked_alt_expr = "reduce(reduce(reduce(1/(reduce(hamming(a,b),sum,z)+1),max,y),sum,x),max,c)";

//-----------------------------------------------------------------------------

void assert_optimized(const TensorSpec& a, const TensorSpec& b, size_t vec_size,
                      const std::string& expr = main_expr) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a);
    param_repo.add("b", b);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(main_expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(main_expr, param_repo));
    auto info = fast_fixture.find_all<SumMaxInvHammingFunction>();
    if (info.size() == 1) {
        EXPECT_TRUE(info[0]->result_is_mutable());
        EXPECT_EQ(info[0]->vec_size(), vec_size);
    }
    EXPECT_EQ(info.size(), 1);
    EXPECT_EQ(fast_fixture.find_all<MaxSumMaxInvHammingFunction>().size(), 0);
}

void assert_not_optimized(const TensorSpec& a, const TensorSpec& b, const std::string& expr = main_expr) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a);
    param_repo.add("b", b);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fast_fixture.find_all<SumMaxInvHammingFunction>();
    EXPECT_EQ(info.size(), 0);
}

void assert_chunked_optimized(const TensorSpec& a, const TensorSpec& b, size_t vec_size, size_t chunk_dim,
                              const std::string&         expr = chunked_expr,
                              const ValueBuilderFactory& factory = prod_factory) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a);
    param_repo.add("b", b);
    EvalFixture slow_fixture(factory, expr, param_repo, false);
    EvalFixture fast_fixture(factory, expr, param_repo, true);
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), slow_fixture.result());
    auto info = fast_fixture.find_all<MaxSumMaxInvHammingFunction>();
    if (info.size() == 1) {
        EXPECT_TRUE(info[0]->result_is_mutable());
        EXPECT_EQ(info[0]->vec_size(), vec_size);
        EXPECT_EQ(info[0]->chunk_dim(), chunk_dim);
    }
    EXPECT_EQ(info.size(), 1);
    EXPECT_EQ(fast_fixture.find_all<SumMaxInvHammingFunction>().size(), 0);
}

// every expression used in this test file reduces down to a scalar
double as_double(const TensorSpec& spec) {
    return spec.cells().begin()->second.value;
}

// The generic path accumulates the per-chunk sum sequentially in float, while
// EvalFixture::ref accumulates in double and rounds to float once. Where enough terms
// are summed the two drift apart by more than the ~1 ULP that comparing TensorSpec
// values allows, so the reference can only be compared against approximately. The
// bit-exact comparison against the unoptimized path still pins the accumulation width
// and order.
void assert_chunked_optimized_approx(const TensorSpec& a, const TensorSpec& b, double rel_epsilon = 1e-5,
                                     const std::string& expr = chunked_expr) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a);
    param_repo.add("b", b);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    double      ref = as_double(EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), slow_fixture.result());
    EXPECT_NEAR(as_double(slow_fixture.result()), ref, std::abs(ref) * rel_epsilon);
    EXPECT_NEAR(as_double(fast_fixture.result()), ref, std::abs(ref) * rel_epsilon);
    EXPECT_EQ(fast_fixture.find_all<MaxSumMaxInvHammingFunction>().size(), 1);
    EXPECT_EQ(fast_fixture.find_all<SumMaxInvHammingFunction>().size(), 0);
}

void assert_chunked_not_optimized(const TensorSpec& a, const TensorSpec& b, const std::string& expr = chunked_expr) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", a);
    param_repo.add("b", b);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fast_fixture.find_all<MaxSumMaxInvHammingFunction>();
    EXPECT_EQ(info.size(), 0);
}

//-----------------------------------------------------------------------------

GenSpec make_spec(const std::string& desc, CellType cell_type) {
    return GenSpec::from_desc(desc).cells(cell_type).seq(
        Seq({0x1f, 0x2e, 0x3d, 0x4c, 0x5b, 0x6a, 0x79, 0x88, 0x97, 0xa6, 0xb5, 0xc4, 0xd3, 0xe2, 0xf1}));
}

GenSpec query = make_spec("x3_1z7", CellType::INT8);
GenSpec document = make_spec("y5_1z7", CellType::INT8);
GenSpec chunked_document = make_spec("c2_1y5_1z7", CellType::INT8);

// a query of all-zero vectors, to be paired with two_bit_document below
GenSpec uniform_query(const std::string& desc) {
    return GenSpec::from_desc(desc).cells(CellType::INT8).seq(Seq({0}));
}

// a single document vector with exactly 2 bits set, so the distance to every all-zero
// query vector is 2 and every inverted distance is exactly 1/(1+2)
GenSpec two_bit_document = GenSpec::from_desc("c1_1y1_1z16")
                               .cells(CellType::INT8)
                               .seq(Seq({3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));

TEST(SumMaxInvHamming, expression_can_be_optimized) {
    assert_optimized(query, document, 7);
}

TEST(SumMaxInvHamming, input_values_can_be_reordered) {
    assert_optimized(document, query, 7);
}

TEST(SumMaxInvHamming, expression_can_have_alternative_form) {
    assert_optimized(query, document, 7, alt_expr);
    assert_optimized(document, query, 7, alt_expr);
}

TEST(SumMaxInvHamming, optimization_works_with_empty_tensors) {
    auto empty_query = make_spec("x0_0z7", CellType::INT8);
    auto empty_document = make_spec("y0_0z7", CellType::INT8);
    assert_optimized(empty_query, document, 7);
    assert_optimized(query, empty_document, 7);
    assert_optimized(empty_query, empty_document, 7);
}

TEST(SumMaxInvHamming, the_hamming_dimension_may_be_trivial) {
    GenSpec trivial_query = make_spec("x3_1z1", CellType::INT8);
    GenSpec trivial_document = make_spec("y5_1z1", CellType::INT8);
    assert_optimized(trivial_query, trivial_document, 1);
}

//-----------------------------------------------------------------------------

TEST(SumMaxInvHamming, other_dimensions_may_be_indexed_as_long_as_hamming_dimension_has_stride_1) {
    auto dense_query = make_spec("x3z7", CellType::INT8);
    auto dense_document = make_spec("y5z7", CellType::INT8);
    assert_optimized(dense_query, dense_document, 7);

    std::string outer_expr = "reduce(reduce(1/(1+reduce(hamming(a,b),sum,y)),max,x),sum,z)";
    auto        dense_query2 = make_spec("x3y7", CellType::INT8);
    auto        dense_document2 = make_spec("y7z5", CellType::INT8);
    assert_not_optimized(dense_query2, dense_document2);
}

//-----------------------------------------------------------------------------

TEST(SumMaxInvHamming, all_cells_must_be_int8) {
    for (auto ct : CellTypeUtils::list_types()) {
        if (ct != CellType::INT8) {
            assert_not_optimized(query.cpy().cells(ct), document);
            assert_not_optimized(query, document.cpy().cells(ct));
            assert_not_optimized(query.cpy().cells(ct), document.cpy().cells(ct));
        }
    }
}

TEST(SumMaxInvHamming, extra_dimensions_are_not_allowed) {
    GenSpec query_es = make_spec("a1_1x3_1z7", CellType::INT8);
    GenSpec query_ed = make_spec("x3_1w1z7", CellType::INT8);
    GenSpec document_es = make_spec("a1_1y5_1z7", CellType::INT8);
    GenSpec document_ed = make_spec("y5_1w1z7", CellType::INT8);
    assert_not_optimized(query_es, document);
    assert_not_optimized(query, document_es);
    assert_not_optimized(query_ed, document);
    assert_not_optimized(query, document_ed);
    assert_not_optimized(query_es, document_es);
    assert_not_optimized(query_ed, document_ed);
}

//-----------------------------------------------------------------------------

TEST(SumMaxInvHamming, chunked_expression_can_be_optimized) {
    assert_chunked_optimized(query, chunked_document, 7, 0);
    assert_chunked_optimized(chunked_document, query, 7, 0);
    assert_chunked_optimized(query, chunked_document, 7, 0, chunked_expr, StreamedValueBuilderFactory::get());
}

TEST(SumMaxInvHamming, the_chunk_dimension_may_come_after_the_document_token_dimension) {
    std::string expr = "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,c),sum,x),max,y)";
    assert_chunked_optimized(query, make_spec("c5_1y2_1z7", CellType::INT8), 7, 1, expr);
}

TEST(SumMaxInvHamming, chunked_expression_can_have_alternative_form) {
    assert_chunked_optimized(query, chunked_document, 7, 0, chunked_alt_expr);
    assert_chunked_optimized(chunked_document, query, 7, 0, chunked_alt_expr);
}

TEST(SumMaxInvHamming, the_chunked_hamming_dimension_may_be_trivial) {
    assert_chunked_optimized(make_spec("x3_1z1", CellType::INT8), make_spec("c2_1y5_1z1", CellType::INT8), 1, 0);
}

TEST(SumMaxInvHamming, chunked_query_dimensions_may_be_indexed) {
    assert_chunked_optimized(make_spec("x3z7", CellType::INT8), chunked_document, 7, 0);
}

TEST(SumMaxInvHamming, chunk_scores_match_generic_evaluation_with_varied_distances) {
    // several chunks and query vectors with distinct distances, so that a difference in
    // the order the per-chunk float sums are accumulated would show up as a mismatch
    auto varied_query = GenSpec::from_desc("x17_1z16")
                            .cells(CellType::INT8)
                            .seq(Seq({0x1f, 0x2e, 0x3d, 0x4c, 0x5b, 0x6a, 0x79, 0x11, 0x03, 0x7f, 0x40, 0x22, 0x13,
                                      0x0e, 0x51, 0x63, 0x77}));
    auto varied_document = GenSpec::from_desc("c11_1y13_1z16")
                               .cells(CellType::INT8)
                               .seq(Seq({0x01, 0x7e, 0x3d, 0x4c, 0x5b, 0x0a, 0x79, 0x18, 0x27, 0x36, 0x45, 0x54,
                                         0x63, 0x72, 0x0f, 0x1e, 0x2d, 0x3c, 0x4b, 0x5a, 0x69, 0x78, 0x07, 0x16,
                                         0x25, 0x34, 0x43, 0x52, 0x61, 0x70, 0x7f, 0x00, 0x11, 0x22, 0x33, 0x44,
                                         0x55, 0x66, 0x77, 0x08, 0x19, 0x2a, 0x3b, 0x4c, 0x5d, 0x6e, 0x7f, 0x10}));
    assert_chunked_optimized(varied_query, varied_document, 16, 0);
    assert_chunked_optimized(varied_document, varied_query, 16, 0);
    assert_chunked_optimized(varied_query, varied_document, 16, 0, chunked_expr, StreamedValueBuilderFactory::get());
}

TEST(SumMaxInvHamming, chunked_optimization_handles_many_chunks) {
    // enough chunks that the per-chunk distance vector is grown repeatedly while scanning
    auto many_chunks = GenSpec::from_desc("c257_1y3_1z8")
                           .cells(CellType::INT8)
                           .seq(Seq({0x01, 0x7e, 0x3d, 0x4c, 0x5b, 0x0a, 0x79, 0x18, 0x27, 0x36, 0x45}));
    assert_chunked_optimized(make_spec("x8_1z8", CellType::INT8), many_chunks, 8, 0);
}

TEST(SumMaxInvHamming, chunked_optimization_works_with_empty_tensors) {
    auto empty_query = make_spec("x0_0z7", CellType::INT8);
    auto empty_document = make_spec("c0_0y0_0z7", CellType::INT8);
    assert_chunked_optimized(empty_query, chunked_document, 7, 0);
    assert_chunked_optimized(query, empty_document, 7, 0);
    assert_chunked_optimized(empty_query, empty_document, 7, 0);
}

TEST(SumMaxInvHamming, chunk_scores_are_accumulated_in_float) {
    // The per-chunk sum lands in a tensor<float>(c{}) during generic evaluation, so the
    // optimization must accumulate in float as well. With 32 all-zero query vectors the
    // chunk score is 32 additions of exactly 1/3, and the ways of getting there disagree
    // in the last bits:
    //
    //   sequential float                    10.666665077209472656
    //   sequential double                   10.666666666666667851
    //   double accumulation rounded once    10.666666984558105469
    //
    // Both fixtures produce the first value, so summing in double would break the
    // bit-exact comparison between them. EvalFixture::ref produces the third value,
    // which is 1.8e-07 away in relative terms -- past exact comparison, but well inside
    // the approximate one.
    assert_chunked_optimized_approx(uniform_query("x32_1z16"), two_bit_document);
}

TEST(SumMaxInvHamming, chunk_scores_drift_from_the_reference_as_the_query_grows) {
    // the float accumulation error grows with the number of terms summed per chunk: the
    // relative difference against the reference is 1.8e-07 at 32 query vectors, 7.2e-07
    // at 256 and 3.0e-06 at 1024, while the two fixtures stay bit-exact all the way
    assert_chunked_optimized_approx(uniform_query("x256_1z16"), two_bit_document);
    assert_chunked_optimized_approx(uniform_query("x1024_1z16"), two_bit_document);
}

TEST(SumMaxInvHamming, chunked_optimization_needs_two_mapped_document_dimensions) {
    assert_chunked_not_optimized(query, make_spec("c2y5_1z7", CellType::INT8));
    assert_chunked_not_optimized(query, make_spec("c2_1y5z7", CellType::INT8));
    // the extra mapped dimension is shared with the query and reduced by the sum,
    // so the result is still a double and the dimension requirements do the rejecting
    assert_chunked_not_optimized(query, make_spec("c2_1x3_1y5_1z7", CellType::INT8));
}

TEST(SumMaxInvHamming, all_chunked_cells_must_be_int8) {
    for (auto ct : CellTypeUtils::list_types()) {
        if (ct != CellType::INT8) {
            assert_chunked_not_optimized(query.cpy().cells(ct), chunked_document);
            assert_chunked_not_optimized(query, chunked_document.cpy().cells(ct));
            assert_chunked_not_optimized(query.cpy().cells(ct), chunked_document.cpy().cells(ct));
        }
    }
}

TEST(SumMaxInvHamming, similar_chunked_expressions_are_not_optimized) {
    // wrong operator or wrong constant when inverting the distance
    assert_chunked_not_optimized(query, chunked_document,
                                 "reduce(reduce(reduce(1*(1+reduce(hamming(a,b),sum,z)),max,y),sum,x),max,c)");
    assert_chunked_not_optimized(query, chunked_document,
                                 "reduce(reduce(reduce(1/(1-reduce(hamming(a,b),sum,z)),max,y),sum,x),max,c)");
    // wrong aggregator in any one of the four reduce steps
    assert_chunked_not_optimized(query, chunked_document,
                                 "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),max,z)),max,y),sum,x),max,c)");
    assert_chunked_not_optimized(query, chunked_document,
                                 "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),sum,y),sum,x),max,c)");
    assert_chunked_not_optimized(query, chunked_document,
                                 "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,y),max,x),max,c)");
    assert_chunked_not_optimized(query, chunked_document,
                                 "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,y),sum,x),sum,c)");
    // the summed dimension must belong to the query and the other two to the document
    assert_chunked_not_optimized(query, chunked_document,
                                 "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,y),sum,c),max,x)");
    assert_chunked_not_optimized(query, chunked_document,
                                 "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,x),sum,y),max,c)");
}

//-----------------------------------------------------------------------------

TEST(SumMaxInvHamming, similar_expressions_are_not_optimized) {
    assert_not_optimized(query, document, "reduce(reduce(1*(1+reduce(hamming(a,b),sum,z)),max,y),sum,x)");
    assert_not_optimized(query, document, "reduce(reduce(1/(1-reduce(hamming(a,b),sum,z)),max,y),sum,x)");
    assert_not_optimized(query, document, "reduce(reduce(1/(1+reduce(hamming(a,b),max,z)),max,y),sum,x)");
    assert_not_optimized(query, document, "reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),sum,y),sum,x)");
    assert_not_optimized(query, document, "reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,y),max,x)");
    assert_not_optimized(query, document, "reduce(reduce(1/(1+reduce(hamming(a,b),sum,y)),max,z),sum,x)");
    assert_not_optimized(query, document, "reduce(reduce(1/(1+reduce(hamming(a,b),sum,x)),max,y),sum,z)");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
