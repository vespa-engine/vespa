// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/mixed_simple_join_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iomanip>
#include <ios>
#include <sstream>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

using vespalib::make_string_short::fmt;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();
const ValueBuilderFactory &test_factory  = SimpleValueBuilderFactory::get();

using Primary = MixedSimpleJoinFunction::Primary;
using Overlap = MixedSimpleJoinFunction::Overlap;

namespace vespalib::eval {

std::ostream &operator<<(std::ostream &os, Primary primary)
{
    switch(primary) {
    case Primary::LHS: return os << "LHS";
    case Primary::RHS: return os << "RHS";
    }
    abort();
}

std::ostream &operator<<(std::ostream &os, Overlap overlap)
{
    switch(overlap) {
    case Overlap::FULL: return os << "FULL";
    case Overlap::INNER: return os << "INNER";
    case Overlap::OUTER: return os << "OUTER";
    }
    abort();
}

}

struct FunInfo {
    using LookFor = MixedSimpleJoinFunction;
    Overlap overlap;
    size_t factor;
    Primary primary;
    bool l_mut;
    bool r_mut;
    bool inplace;
    void verify(const EvalFixture &fixture, const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQ(fun.overlap(), overlap);
        EXPECT_EQ(fun.factor(), factor);
        EXPECT_EQ(fun.primary(), primary);
        if (fun.primary_is_mutable()) {
            if (fun.primary() == Primary::LHS) {
                EXPECT_TRUE(l_mut);
            }
            if (fun.primary() == Primary::RHS) {
                EXPECT_TRUE(r_mut);
            }
        }
        EXPECT_EQ(fun.inplace(), inplace);
        if (fun.inplace()) {
            EXPECT_TRUE(fun.primary_is_mutable());
            size_t idx = (fun.primary() == Primary::LHS) ? 0 : 1;
            EXPECT_EQ(fixture.result_value().cells().data, fixture.param_value(idx).cells().data);
            EXPECT_NE(fixture.result_value().cells().data, fixture.param_value(1-idx).cells().data);
        } else {
            EXPECT_NE(fixture.result_value().cells().data, fixture.param_value(0).cells().data);
            EXPECT_NE(fixture.result_value().cells().data, fixture.param_value(1).cells().data);
        }
    }
};

void verify_simple(const std::string &expr, Primary primary, Overlap overlap, size_t factor,
                   bool l_mut, bool r_mut, bool inplace)
{
    std::ostringstream os;
    os << "verify_simple(" << std::quoted(expr) << ", " << primary << ", " << overlap << ", " << factor << ", " <<
        std::boolalpha << l_mut << ", " << r_mut << ", " << inplace << ")";
    SCOPED_TRACE(os.str());
    FunInfo details{overlap, factor, primary, l_mut, r_mut, inplace};
    {
        SCOPED_TRACE("double");
        CellTypeSpace just_double({CellType::DOUBLE}, 2);
        EvalFixture::verify<FunInfo>(expr, {details}, just_double);
    }
    {
        SCOPED_TRACE("float");
        CellTypeSpace just_float({CellType::FLOAT}, 2);
        EvalFixture::verify<FunInfo>(expr, {details}, just_float);
    }
}

void verify_optimized(const std::string &expr, Primary primary, Overlap overlap, size_t factor,
                      bool l_mut = false, bool r_mut = false, bool inplace = false)
{
    std::ostringstream os;
    os << "verify_optimized(" << std::quoted(expr) << ", " << primary << ", " << overlap << ", " << factor << ", " <<
        std::boolalpha << l_mut << ", " << r_mut << ", " << inplace << ")";
    SCOPED_TRACE(os.str());
    CellTypeSpace all_types(CellTypeUtils::list_types(), 2);
    FunInfo details{overlap, factor, primary, l_mut, r_mut, inplace};
    EvalFixture::verify<FunInfo>(expr, {details}, all_types);
}

void verify_not_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

TEST(MixedSimpleJoinFunctionTest, require_that_basic_join_is_optimized)
{
    verify_optimized("y5+y5$2", Primary::RHS, Overlap::FULL, 1);
}

TEST(MixedSimpleJoinFunctionTest, require_that_inplace_is_preferred)
{
    verify_simple("y5+y5$2", Primary::RHS, Overlap::FULL, 1, false, false, false);
    verify_simple("y5+@y5$2", Primary::RHS, Overlap::FULL, 1, false, true, true);
    verify_simple("@y5+@y5$2", Primary::RHS, Overlap::FULL, 1, true, true, true);
    verify_simple("@y5+y5$2", Primary::LHS, Overlap::FULL, 1, true, false, true);
}

TEST(MixedSimpleJoinFunctionTest, require_that_unit_join_is_optimized)
{
    verify_optimized("a1b1c1+x1y1z1", Primary::RHS, Overlap::FULL, 1);
}

TEST(MixedSimpleJoinFunctionTest, require_that_trivial_dimensions_do_not_affect_overlap_calculation)
{
    verify_optimized("c5d1+b1c5", Primary::RHS, Overlap::FULL, 1);
    verify_simple("@c5d1+@b1c5", Primary::RHS, Overlap::FULL, 1, true, true, true);
}

TEST(MixedSimpleJoinFunctionTest, require_that_outer_nesting_is_preferred_to_inner_nesting)
{
    verify_optimized("a1b1c1+y5", Primary::RHS, Overlap::OUTER, 5);
}

TEST(MixedSimpleJoinFunctionTest, require_that_non_subset_join_is_not_optimized)
{
    verify_not_optimized("y5+z3");
}

TEST(MixedSimpleJoinFunctionTest, require_that_subset_join_with_complex_overlap_is_not_optimized)
{
    verify_not_optimized("x3y5z3+y5");
}

struct LhsRhs {
    std::string lhs;
    std::string rhs;
    size_t lhs_size;
    size_t rhs_size;
    Overlap overlap;
    size_t factor;
    LhsRhs(const std::string &lhs_in, const std::string &rhs_in,
           size_t lhs_size_in, size_t rhs_size_in, Overlap overlap_in) noexcept
        : lhs(lhs_in), rhs(rhs_in), lhs_size(lhs_size_in), rhs_size(rhs_size_in), overlap(overlap_in), factor(1)
    {
        if (lhs_size > rhs_size) {
            EXPECT_EQ(lhs_size % rhs_size, 0u);
            factor = (lhs_size / rhs_size);
        } else {
            EXPECT_EQ(rhs_size % lhs_size, 0u);
            factor = (rhs_size / lhs_size);
        }
    }
    ~LhsRhs();
};

LhsRhs::~LhsRhs() = default;

TEST(MixedSimpleJoinFunctionTest, require_that_various_parameter_combinations_work)
{
    for (CellType lct : CellTypeUtils::list_types()) {
        for (CellType rct : CellTypeUtils::list_types()) {
            for (bool left_mut: {false, true}) {
                for (bool right_mut: {false, true}) {
                    for (const char * expr: {"a+b", "a-b", "a*b"}) {
                        for (const LhsRhs &params:
                                 {       LhsRhs("y5",   "y5", 5,  5, Overlap::FULL),
                                         LhsRhs("y5", "x3y5", 5, 15, Overlap::INNER),
                                         LhsRhs("y5", "y5z3", 5, 15, Overlap::OUTER),
                                         LhsRhs("x3y5", "y5", 15, 5, Overlap::INNER),
                                         LhsRhs("y5z3", "y5", 15, 5, Overlap::OUTER)})
                        {
                            EvalFixture::ParamRepo param_repo;
                            auto a_spec = GenSpec::from_desc(params.lhs).cells(lct).seq(AX_B(0.25, 1.125));
                            auto b_spec = GenSpec::from_desc(params.rhs).cells(rct).seq(AX_B(-0.25, 25.0));
                            if (left_mut) {
                                param_repo.add_mutable("a", a_spec);
                            } else {
                                param_repo.add("a", a_spec);
                            }
                            if (right_mut) {
                                param_repo.add_mutable("b", b_spec);
                            } else {
                                param_repo.add("b", b_spec);
                            }
                            SCOPED_TRACE(expr);
                            CellType result_ct = CellMeta::join(CellMeta{lct, false}, CellMeta{rct, false}).cell_type;
                            Primary primary = Primary::RHS;
                            if (params.overlap == Overlap::FULL) {
                                bool w_lhs = (lct == result_ct) && left_mut;
                                bool w_rhs = (rct == result_ct) && right_mut;
                                if (w_lhs && !w_rhs) {
                                    primary = Primary::LHS;
                                }
                            } else if (params.lhs_size > params.rhs_size) {
                                primary = Primary::LHS;
                            }
                            bool pri_mut = (primary == Primary::LHS) ? left_mut : right_mut;
                            bool pri_same_ct = (primary == Primary::LHS) ? (lct == result_ct) : (rct == result_ct);
                            bool inplace = (pri_mut && pri_same_ct);
                            auto expect = EvalFixture::ref(expr, param_repo);
                            EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
                            EvalFixture test_fixture(test_factory, expr, param_repo, true, true);
                            EvalFixture fixture(prod_factory, expr, param_repo, true, true);
                            EXPECT_EQ(fixture.result(), expect);
                            EXPECT_EQ(slow_fixture.result(), expect);
                            EXPECT_EQ(test_fixture.result(), expect);
                            auto info = fixture.find_all<FunInfo::LookFor>();
                            ASSERT_EQ(info.size(), 1u);
                            FunInfo details{params.overlap, params.factor, primary, left_mut, right_mut, inplace};
                            details.verify(fixture, *info[0]);
                        }
                    }
                }
            }
        }
    }
}

TEST(MixedSimpleJoinFunctionTest, require_that_scalar_values_are_not_optimized)
{
    verify_not_optimized("reduce(v3,sum)+reduce(v4,sum)");
    verify_not_optimized("reduce(v3,sum)+y5");
    verify_not_optimized("y5+reduce(v3,sum)");
    verify_not_optimized("reduce(v3,sum)+x3_1");
    verify_not_optimized("x3_1+reduce(v3,sum)");
    verify_not_optimized("reduce(v3,sum)+x3_1y5z3");
    verify_not_optimized("x3_1y5z3+reduce(v3,sum)");
}

TEST(MixedSimpleJoinFunctionTest, require_that_sparse_tensors_are_mostly_not_optimized)
{
    verify_not_optimized("x3_1+x3_1$2");
    verify_not_optimized("x3_1+y5");
    verify_not_optimized("y5+x3_1");
    verify_not_optimized("x3_1+x3_1y5z3");
    verify_not_optimized("x3_1y5z3+x3_1");
}

TEST(MixedSimpleJoinFunctionTest, require_that_sparse_tensor_joined_with_trivial_dense_tensor_is_optimized)
{
    verify_optimized("x3_1+a1b1c1", Primary::LHS, Overlap::FULL, 1);
    verify_optimized("a1b1c1+x3_1", Primary::RHS, Overlap::FULL, 1);
}

TEST(MixedSimpleJoinFunctionTest, require_that_primary_tensor_can_be_empty)
{
    verify_optimized("x0_1y5z3+y5z3", Primary::LHS, Overlap::FULL, 1);
    verify_optimized("y5z3+x0_1y5z3", Primary::RHS, Overlap::FULL, 1);
}

TEST(MixedSimpleJoinFunctionTest, require_that_mixed_tensors_can_be_optimized)
{
    verify_not_optimized("x3_1y5z3+x3_1y5z3$2");
    verify_optimized("x3_1y5z3+y5z3", Primary::LHS, Overlap::FULL,   1);
    verify_optimized("x3_1y5z3+y5",   Primary::LHS, Overlap::OUTER,  3);
    verify_optimized("x3_1y5z3+z3",   Primary::LHS, Overlap::INNER,  5);
    verify_optimized("y5z3+x3_1y5z3", Primary::RHS, Overlap::FULL,   1);
    verify_optimized("y5+x3_1y5z3",   Primary::RHS, Overlap::OUTER,  3);
    verify_optimized("z3+x3_1y5z3",   Primary::RHS, Overlap::INNER,  5);
}

TEST(MixedSimpleJoinFunctionTest, require_that_mixed_tensors_can_be_inplace)
{
    verify_simple("@x3_1y5z3+y5z3",  Primary::LHS, Overlap::FULL,   1, true, false, true);
    verify_simple("@x3_1y5z3+y5",    Primary::LHS, Overlap::OUTER,  3, true, false, true);
    verify_simple("@x3_1y5z3+z3",    Primary::LHS, Overlap::INNER,  5, true, false, true);
    verify_simple("@x3_1y5z3+@y5z3", Primary::LHS, Overlap::FULL,   1, true,  true, true);
    verify_simple("@x3_1y5z3+@y5",   Primary::LHS, Overlap::OUTER,  3, true,  true, true);
    verify_simple("@x3_1y5z3+@z3",   Primary::LHS, Overlap::INNER,  5, true,  true, true);
    verify_simple("y5z3+@x3_1y5z3",  Primary::RHS, Overlap::FULL,   1, false, true, true);
    verify_simple("y5+@x3_1y5z3",    Primary::RHS, Overlap::OUTER,  3, false, true, true);
    verify_simple("z3+@x3_1y5z3",    Primary::RHS, Overlap::INNER,  5, false, true, true);
    verify_simple("@y5z3+@x3_1y5z3", Primary::RHS, Overlap::FULL,   1, true,  true, true);
    verify_simple("@y5+@x3_1y5z3",   Primary::RHS, Overlap::OUTER,  3, true,  true, true);
    verify_simple("@z3+@x3_1y5z3",   Primary::RHS, Overlap::INNER,  5, true,  true, true);
}

GTEST_MAIN_RUN_ALL_TESTS()
