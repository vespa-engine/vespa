// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/instruction/sparse_singledim_lookup.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

//-----------------------------------------------------------------------------

struct FunInfo {
    using LookFor = SparseSingledimLookup;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
    }
};

void verify_optimized(const vespalib::string &expr) {
    CellTypeSpace type_space(CellTypeUtils::list_types(), 1);
    EvalFixture::verify<FunInfo>(expr, {FunInfo()}, type_space);
}

void verify_not_optimized(const vespalib::string &expr) {
    CellTypeSpace just_float({CellType::FLOAT}, 1);
    EvalFixture::verify<FunInfo>(expr, {}, just_float);
}

//-----------------------------------------------------------------------------

TEST(SparseSingledimLookup, expression_can_be_optimized) {
    verify_optimized("x5_1{x:(1+2)}");
}

TEST(SparseSingledimLookup, optimized_expression_handles_failed_lookup) {
    verify_optimized("x5_1{x:(5+5)}");
    verify_optimized("x5_1{x:(5-10)}");
}

TEST(SparseSingledimLookup, verbatim_expression_is_not_optimized) {
    verify_not_optimized("x5_1{x:3}");
    verify_not_optimized("x5_1{x:(3)}");
}

TEST(SparseSingledimLookup, similar_expressions_are_not_optimized) {
    verify_not_optimized("x5{x:(1+2)}");
    verify_not_optimized("x5_1y3{x:(1+2)}");
    verify_not_optimized("x5_1y3_1{x:(1+2)}");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
