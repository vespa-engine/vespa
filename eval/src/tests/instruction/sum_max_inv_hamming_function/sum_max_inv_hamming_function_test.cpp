// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/sum_max_inv_hamming_function.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory& prod_factory = FastValueBuilderFactory::get();

std::string main_expr = "reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,y),sum,x)";
std::string alt_expr = "reduce(reduce(1/(reduce(hamming(a,b),sum,z)+1),max,y),sum,x)";
std::string chunked_expr = "reduce(reduce(reduce(1/(1+reduce(hamming(a,b),sum,z)),max,y),sum,x),max,c)";

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

TEST(SumMaxInvHamming, chunked_optimization_works_with_empty_tensors) {
    auto empty_query = make_spec("x0_0z7", CellType::INT8);
    auto empty_document = make_spec("c0_0y0_0z7", CellType::INT8);
    assert_chunked_optimized(empty_query, chunked_document, 7, 0);
    assert_chunked_optimized(query, empty_document, 7, 0);
    assert_chunked_optimized(empty_query, empty_document, 7, 0);
}

TEST(SumMaxInvHamming, chunk_scores_are_accumulated_in_float) {
    // generic evaluation sums the inverted distances into float cells; summing them in double would differ
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", GenSpec::from_desc("x32_1z16").cells(CellType::INT8).seq(Seq({0})));
    param_repo.add("b", GenSpec::from_desc("c1_1y1_1z16")
                            .cells(CellType::INT8)
                            .seq(Seq({3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})));
    EvalFixture slow_fixture(prod_factory, chunked_expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, chunked_expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), slow_fixture.result());
    EXPECT_EQ(fast_fixture.find_all<MaxSumMaxInvHammingFunction>().size(), 1);
}

TEST(SumMaxInvHamming, chunked_optimization_needs_two_mapped_document_dimensions) {
    assert_chunked_not_optimized(query, make_spec("c2y5_1z7", CellType::INT8));
    assert_chunked_not_optimized(query, make_spec("c2_1y5z7", CellType::INT8));
    assert_chunked_not_optimized(query, make_spec("c2_1d3_1y5_1z7", CellType::INT8));
    assert_chunked_not_optimized(query.cpy().cells(CellType::DOUBLE), chunked_document.cpy().cells(CellType::DOUBLE));
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
