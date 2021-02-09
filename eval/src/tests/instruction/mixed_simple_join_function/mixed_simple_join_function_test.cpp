// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/mixed_simple_join_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>

#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

using vespalib::make_string_short::fmt;

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

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", GenSpec(1.5))
        .add("b", GenSpec(2.5))
        .add("sparse", GenSpec().map("x", {"a", "b", "c"}))
        .add("mixed", GenSpec().map("x", {"a", "b", "c"}).idx("y", 5).idx("z", 3))
        .add("empty_mixed", GenSpec().map("x", {}).idx("y", 5).idx("z", 3))
        .add_mutable("@mixed", GenSpec().map("x", {"a", "b", "c"}).idx("y", 5).idx("z", 3))
        .add_variants("a1b1c1", GenSpec().idx("a", 1).idx("b", 1).idx("c", 1))
        .add_variants("x1y1z1", GenSpec().idx("x", 1).idx("y", 1).idx("z", 1))
        .add_variants("x3y5z3", GenSpec().idx("x", 3).idx("y", 5).idx("z", 3))
        .add_variants("z3", GenSpec().idx("z", 3))
        .add_variants("c5d1", GenSpec().idx("c", 5).idx("d", 1))
        .add_variants("b1c5", GenSpec().idx("b", 1).idx("c", 5))
        .add_variants("x3y5", GenSpec().idx("x", 3).idx("y", 5).seq([](size_t idx) noexcept { return double((idx * 2) + 3); }))
        .add_variants("x3y5$2", GenSpec().idx("x", 3).idx("y", 5).seq([](size_t idx) noexcept { return double((idx * 3) + 2); }))
        .add_variants("y5", GenSpec().idx("y", 5).seq([](size_t idx) noexcept { return double((idx * 2) + 3); }))
        .add_variants("y5$2", GenSpec().idx("y", 5).seq([](size_t idx) noexcept { return double((idx * 3) + 2); }))
        .add_variants("y5z3", GenSpec().idx("y", 5).idx("z", 3).seq([](size_t idx) noexcept { return double((idx * 2) + 3); }))
        .add_variants("y5z3$2", GenSpec().idx("y", 5).idx("z", 3).seq([](size_t idx) noexcept { return double((idx * 3) + 2); }));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, Primary primary, Overlap overlap, bool pri_mut, size_t factor, int p_inplace = -1) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<MixedSimpleJoinFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQUAL(info[0]->primary(), primary);
    EXPECT_EQUAL(info[0]->overlap(), overlap);
    EXPECT_EQUAL(info[0]->primary_is_mutable(), pri_mut);
    EXPECT_EQUAL(info[0]->factor(), factor);
    EXPECT_TRUE((p_inplace == -1) || (fixture.num_params() > size_t(p_inplace)));
    for (size_t i = 0; i < fixture.num_params(); ++i) {
        if (i == size_t(p_inplace)) {
            EXPECT_EQUAL(fixture.get_param(i), fixture.result());
        } else {
            if (!fixture.result().cells().empty()) {
                EXPECT_NOT_EQUAL(fixture.get_param(i), fixture.result());
            }
        }
    }
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<MixedSimpleJoinFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that basic join is optimized") {
    TEST_DO(verify_optimized("y5+y5$2", Primary::RHS, Overlap::FULL, false, 1));
}

TEST("require that unit join is optimized") {
    TEST_DO(verify_optimized("a1b1c1+x1y1z1", Primary::RHS, Overlap::FULL, false, 1));
}

TEST("require that trivial dimensions do not affect overlap calculation") {
    TEST_DO(verify_optimized("c5d1+b1c5", Primary::RHS, Overlap::FULL, false, 1));
}

TEST("require that outer nesting is preferred to inner nesting") {
    TEST_DO(verify_optimized("a1b1c1+y5", Primary::RHS, Overlap::OUTER, false, 5));
}

TEST("require that non-subset join is not optimized") {
    TEST_DO(verify_not_optimized("y5+z3"));
}

TEST("require that subset join with complex overlap is not optimized") {
    TEST_DO(verify_not_optimized("x3y5z3+y5"));
}

struct LhsRhs {
    vespalib::string lhs;
    vespalib::string rhs;
    size_t lhs_size;
    size_t rhs_size;
    Overlap overlap;
    size_t factor;
    LhsRhs(const vespalib::string &lhs_in, const vespalib::string &rhs_in,
           size_t lhs_size_in, size_t rhs_size_in, Overlap overlap_in) noexcept
        : lhs(lhs_in), rhs(rhs_in), lhs_size(lhs_size_in), rhs_size(rhs_size_in), overlap(overlap_in), factor(1)
    {
        if (lhs_size > rhs_size) {
            ASSERT_EQUAL(lhs_size % rhs_size, 0u);
            factor = (lhs_size / rhs_size);
        } else {
            ASSERT_EQUAL(rhs_size % lhs_size, 0u);
            factor = (rhs_size / lhs_size);
        }
    }
};

vespalib::string adjust_param(const vespalib::string &str, bool float_cells, bool mut_cells, bool is_rhs) {
    vespalib::string result = str;
    if (mut_cells) {
        result = "@" + result;
    }
    if (is_rhs) {
        result += "$2";
    }
    if (float_cells) {
        result += "_f";
    }
    return result;
}

TEST("require that various parameter combinations work") {
    for (bool left_float: {false, true}) {
        for (bool right_float: {false, true}) {
            bool float_result = (left_float && right_float);
            for (bool left_mut: {false, true}) {
                for (bool right_mut: {false, true}) {
                    for (const char *op_pattern: {"%s+%s", "%s-%s", "%s*%s"}) {
                        for (const LhsRhs &params:
                            {       LhsRhs("y5",   "y5", 5,  5, Overlap::FULL),
                                    LhsRhs("y5", "x3y5", 5, 15, Overlap::INNER),
                                    LhsRhs("y5", "y5z3", 5, 15, Overlap::OUTER),
                                    LhsRhs("x3y5", "y5", 15, 5, Overlap::INNER),
                                    LhsRhs("y5z3", "y5", 15, 5, Overlap::OUTER)})
                        {
                            vespalib::string left = adjust_param(params.lhs, left_float, left_mut, false);
                            vespalib::string right = adjust_param(params.rhs, right_float, right_mut, true);
                            vespalib::string expr = fmt(op_pattern, left.c_str(), right.c_str());
                            TEST_STATE(expr.c_str());
                            Primary primary = Primary::RHS;
                            if (params.overlap == Overlap::FULL) {
                                bool w_lhs = ((left_float == float_result) && left_mut);
                                bool w_rhs = ((right_float == float_result) && right_mut);
                                if (w_lhs && !w_rhs) {
                                    primary = Primary::LHS;
                                }
                            } else if (params.lhs_size > params.rhs_size) {
                                primary = Primary::LHS;
                            }
                            bool pri_mut = (primary == Primary::LHS) ? left_mut : right_mut;
                            bool pri_float = (primary == Primary::LHS) ? left_float : right_float;
                            int p_inplace = -1;
                            if (pri_mut && (pri_float == float_result)) {
                                p_inplace = (primary == Primary::LHS) ? 0 : 1;
                            }
                            verify_optimized(expr, primary, params.overlap, pri_mut, params.factor, p_inplace);
                        }
                    }
                }
            }
        }
    }
}

TEST("require that scalar values are not optimized") {
    TEST_DO(verify_not_optimized("a+b"));
    TEST_DO(verify_not_optimized("a+y5"));
    TEST_DO(verify_not_optimized("y5+b"));
    TEST_DO(verify_not_optimized("a+sparse"));
    TEST_DO(verify_not_optimized("sparse+a"));
    TEST_DO(verify_not_optimized("a+mixed"));
    TEST_DO(verify_not_optimized("mixed+a"));
}

TEST("require that sparse tensors are mostly not optimized") {
    TEST_DO(verify_not_optimized("sparse+sparse"));
    TEST_DO(verify_not_optimized("sparse+y5"));
    TEST_DO(verify_not_optimized("y5+sparse"));
    TEST_DO(verify_not_optimized("sparse+mixed"));
    TEST_DO(verify_not_optimized("mixed+sparse"));
}

TEST("require that sparse tensor joined with trivial dense tensor is optimized") {
    TEST_DO(verify_optimized("sparse+a1b1c1", Primary::LHS, Overlap::FULL, false, 1));
    TEST_DO(verify_optimized("a1b1c1+sparse", Primary::RHS, Overlap::FULL, false, 1));
}

TEST("require that primary tensor can be empty") {
    TEST_DO(verify_optimized("empty_mixed+y5z3", Primary::LHS, Overlap::FULL, false, 1));
    TEST_DO(verify_optimized("y5z3+empty_mixed", Primary::RHS, Overlap::FULL, false, 1));
}

TEST("require that mixed tensors can be optimized") {
    TEST_DO(verify_not_optimized("mixed+mixed"));
    TEST_DO(verify_optimized("mixed+y5z3", Primary::LHS, Overlap::FULL,  false, 1));
    TEST_DO(verify_optimized("mixed+y5",   Primary::LHS, Overlap::OUTER, false, 3));
    TEST_DO(verify_optimized("mixed+z3",   Primary::LHS, Overlap::INNER, false, 5));
    TEST_DO(verify_optimized("y5z3+mixed", Primary::RHS, Overlap::FULL,  false, 1));
    TEST_DO(verify_optimized("y5+mixed",   Primary::RHS, Overlap::OUTER, false, 3));
    TEST_DO(verify_optimized("z3+mixed",   Primary::RHS, Overlap::INNER, false, 5));
}

TEST("require that mixed tensors can be inplace") {
    TEST_DO(verify_optimized("@mixed+y5z3", Primary::LHS, Overlap::FULL,  true, 1, 0));
    TEST_DO(verify_optimized("@mixed+y5",   Primary::LHS, Overlap::OUTER, true, 3, 0));
    TEST_DO(verify_optimized("@mixed+z3",   Primary::LHS, Overlap::INNER, true, 5, 0));
    TEST_DO(verify_optimized("y5z3+@mixed", Primary::RHS, Overlap::FULL,  true, 1, 1));
    TEST_DO(verify_optimized("y5+@mixed",   Primary::RHS, Overlap::OUTER, true, 3, 1));
    TEST_DO(verify_optimized("z3+@mixed",   Primary::RHS, Overlap::INNER, true, 5, 1));
}

TEST_MAIN() { TEST_RUN_ALL(); }
