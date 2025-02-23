// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/join_with_number_function.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/unwind_message.h>
#include <ios>
#include <sstream>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

using vespalib::make_string_short::fmt;

using Primary = JoinWithNumberFunction::Primary;

namespace vespalib::eval {

std::ostream &operator<<(std::ostream &os, Primary primary)
{
    switch(primary) {
    case Primary::LHS: return os << "LHS";
    case Primary::RHS: return os << "RHS";
    }
    abort();
}

}

struct FunInfo {
    using LookFor = JoinWithNumberFunction;
    Primary primary;
    bool pri_mut;
    bool inplace;
    void verify(const EvalFixture &fixture, const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQ(fun.primary(), primary);
        EXPECT_EQ(fun.primary_is_mutable(), pri_mut);
        if (inplace) {
            size_t idx = (fun.primary() == Primary::LHS) ? 0 : 1;
            EXPECT_EQ(fixture.result_value().cells().data, fixture.param_value(idx).cells().data);
        }
    }
};

void verify_optimized(const std::string &expr, Primary primary, bool pri_mut) {
    std::ostringstream os;
    os << "verify_uptimized(\"" << expr << "\", " << primary << ", " << std::boolalpha << pri_mut << ")";
    SCOPED_TRACE(os.str());
    UNWIND_MSG("optimize %s", expr.c_str());
    const CellTypeSpace stable_types(CellTypeUtils::list_stable_types(), 2);
    FunInfo stable_details{primary, pri_mut, pri_mut};
    {
        SCOPED_TRACE("stable details");
        EvalFixture::verify<FunInfo>(expr, {stable_details}, stable_types);
    }
    const CellTypeSpace unstable_types(CellTypeUtils::list_unstable_types(), 2);
    FunInfo unstable_details{primary, pri_mut, false};
    {
        SCOPED_TRACE("unstable details");
        EvalFixture::verify<FunInfo>(expr, {unstable_details}, unstable_types);
    }
}

void verify_not_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    UNWIND_MSG("not: %s", expr.c_str());
    CellTypeSpace all_types(CellTypeUtils::list_types(), 2);
    EvalFixture::verify<FunInfo>(expr, {}, all_types);
}

TEST(JoinWithNumberFunctionTest, require_that_dense_number_join_can_be_optimized)
{
    verify_optimized("x3y5+reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)+x3y5", Primary::RHS, false);
    verify_optimized("x3y5*reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)*x3y5", Primary::RHS, false);
}

TEST(JoinWithNumberFunctionTest, require_that_dense_number_join_can_be_inplace)
{
    verify_optimized("@x3y5*reduce(v3,sum)", Primary::LHS, true);
    verify_optimized("reduce(v3,sum)*@x3y5", Primary::RHS, true);
    verify_optimized("@x3y5+reduce(v3,sum)", Primary::LHS, true);
    verify_optimized("reduce(v3,sum)+@x3y5", Primary::RHS, true);
}

TEST(JoinWithNumberFunctionTest, require_that_asymmetric_operations_work)
{
    verify_optimized("x3y5/reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)/x3y5", Primary::RHS, false);
    verify_optimized("x3y5-reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)-x3y5", Primary::RHS, false);
}

TEST(JoinWithNumberFunctionTest, require_that_sparse_number_join_can_be_optimized)
{
    verify_optimized("x3_1z2_1+reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)+x3_1z2_1", Primary::RHS, false);
    verify_optimized("x3_1z2_1<reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)<x3_1z2_1", Primary::RHS, false);
    verify_optimized("x3_1z2_1+reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)+x3_1z2_1", Primary::RHS, false);
    verify_optimized("x3_1z2_1<reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)<x3_1z2_1", Primary::RHS, false);
}

TEST(JoinWithNumberFunctionTest, require_that_sparse_number_join_can_be_inplace)
{
    verify_optimized("@x3_1z2_1+reduce(v3,sum)", Primary::LHS, true);
    verify_optimized("reduce(v3,sum)+@x3_1z2_1", Primary::RHS, true);
    verify_optimized("@x3_1z2_1<reduce(v3,sum)", Primary::LHS, true);
    verify_optimized("reduce(v3,sum)<@x3_1z2_1", Primary::RHS, true);
}

TEST(JoinWithNumberFunctionTest, require_that_mixed_number_join_can_be_optimized)
{
    verify_optimized("x3_1y5z2_1+reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)+x3_1y5z2_1", Primary::RHS, false);
    verify_optimized("x3_1y5z2_1<reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)<x3_1y5z2_1", Primary::RHS, false);
    verify_optimized("x3_1y5z2_1+reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)+x3_1y5z2_1", Primary::RHS, false);
    verify_optimized("x3_1y5z2_1<reduce(v3,sum)", Primary::LHS, false);
    verify_optimized("reduce(v3,sum)<x3_1y5z2_1", Primary::RHS, false);
}

TEST(JoinWithNumberFunctionTest, require_that_mixed_number_join_can_be_inplace)
{
    verify_optimized("@x3_1y5z2_1+reduce(v3,sum)", Primary::LHS, true);
    verify_optimized("reduce(v3,sum)+@x3_1y5z2_1", Primary::RHS, true);
    verify_optimized("@x3_1y5z2_1<reduce(v3,sum)", Primary::LHS, true);
    verify_optimized("reduce(v3,sum)<@x3_1y5z2_1", Primary::RHS, true);
}

TEST(JoinWithNumberFunctionTest, require_that_inappropriate_cases_are_not_optimized)
{
    for (std::string lhs: {"y5", "x3_1z2_1", "x3_1y5z2_1"}) {
        for (std::string rhs: {"y5", "x3_1z2_1", "x3_1y5z2_1"}) {
            auto expr = fmt("%s$1*%s$2", lhs.c_str(), rhs.c_str());
            verify_not_optimized(expr);
        }
        verify_optimized(fmt("reduce(v3,sum)*%s", lhs.c_str()), Primary::RHS, false);
        verify_optimized(fmt("%s*reduce(v3,sum)", lhs.c_str()), Primary::LHS, false);
    }
    // two scalars -> not optimized
    verify_not_optimized("reduce(v3,sum)*reduce(k4,sum)");
}

GTEST_MAIN_RUN_ALL_TESTS()
