// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/join_with_number_function.h>

#include <vespa/vespalib/util/stringfmt.h>

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

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    auto repo = EvalFixture::ParamRepo()
        .add("a", GenSpec(1.5))
        .add("number", GenSpec(2.5))
        .add("dense", GenSpec().idx("y", 5))
        .add_variants("x3y5", GenSpec().idx("x", 3).idx("y", 5))
        .add_variants("mixed", GenSpec().map("x", {"a"}).idx("y", 5).map("z", {"d","e"}))
        .add_variants("sparse", GenSpec().map("x", {"a","b","c"}).map("z", {"d","e","f"}));
    return repo;
}

EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, Primary primary, bool inplace) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<JoinWithNumberFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQUAL(info[0]->primary(), primary);
    EXPECT_EQUAL(info[0]->inplace(), inplace);
    int p_inplace = inplace ? ((primary == Primary::LHS) ? 0 : 1) : -1;
    EXPECT_TRUE((p_inplace == -1) || (fixture.num_params() > size_t(p_inplace)));
    for (size_t i = 0; i < fixture.num_params(); ++i) {
        if (i == size_t(p_inplace)) {
            EXPECT_EQUAL(fixture.get_param(i), fixture.result());
        } else {
            EXPECT_NOT_EQUAL(fixture.get_param(i), fixture.result());
        }
    }
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<JoinWithNumberFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that dense number join can be optimized") {
    TEST_DO(verify_optimized("x3y5+a", Primary::LHS, false));
    TEST_DO(verify_optimized("a+x3y5", Primary::RHS, false));
    TEST_DO(verify_optimized("x3y5_f*a", Primary::LHS, false));
    TEST_DO(verify_optimized("a*x3y5_f", Primary::RHS, false));
}

TEST("require that dense number join can be inplace") {
    TEST_DO(verify_optimized("@x3y5*a", Primary::LHS, true));
    TEST_DO(verify_optimized("a*@x3y5", Primary::RHS, true));
    TEST_DO(verify_optimized("@x3y5_f+a", Primary::LHS, true));
    TEST_DO(verify_optimized("a+@x3y5_f", Primary::RHS, true));
}

TEST("require that asymmetric operations work") {
    TEST_DO(verify_optimized("x3y5/a", Primary::LHS, false));
    TEST_DO(verify_optimized("a/x3y5", Primary::RHS, false));
    TEST_DO(verify_optimized("x3y5_f-a", Primary::LHS, false));
    TEST_DO(verify_optimized("a-x3y5_f", Primary::RHS, false));
}

TEST("require that sparse number join can be optimized") {
    TEST_DO(verify_optimized("sparse+a", Primary::LHS, false));
    TEST_DO(verify_optimized("a+sparse", Primary::RHS, false));
    TEST_DO(verify_optimized("sparse<a", Primary::LHS, false));
    TEST_DO(verify_optimized("a<sparse", Primary::RHS, false));
    TEST_DO(verify_optimized("sparse_f+a", Primary::LHS, false));
    TEST_DO(verify_optimized("a+sparse_f", Primary::RHS, false));
    TEST_DO(verify_optimized("sparse_f<a", Primary::LHS, false));
    TEST_DO(verify_optimized("a<sparse_f", Primary::RHS, false));
}

TEST("require that sparse number join can be inplace") {
    TEST_DO(verify_optimized("@sparse+a", Primary::LHS, true));
    TEST_DO(verify_optimized("a+@sparse_f", Primary::RHS, true));
    TEST_DO(verify_optimized("@sparse_f<a", Primary::LHS, true));
    TEST_DO(verify_optimized("a<@sparse", Primary::RHS, true));
}

TEST("require that mixed number join can be optimized") {
    TEST_DO(verify_optimized("mixed+a", Primary::LHS, false));
    TEST_DO(verify_optimized("a+mixed", Primary::RHS, false));
    TEST_DO(verify_optimized("mixed<a", Primary::LHS, false));
    TEST_DO(verify_optimized("a<mixed", Primary::RHS, false));
    TEST_DO(verify_optimized("mixed_f+a", Primary::LHS, false));
    TEST_DO(verify_optimized("a+mixed_f", Primary::RHS, false));
    TEST_DO(verify_optimized("mixed_f<a", Primary::LHS, false));
    TEST_DO(verify_optimized("a<mixed_f", Primary::RHS, false));
}

TEST("require that mixed number join can be inplace") {
    TEST_DO(verify_optimized("@mixed+a", Primary::LHS, true));
    TEST_DO(verify_optimized("a+@mixed_f", Primary::RHS, true));
    TEST_DO(verify_optimized("@mixed_f<a", Primary::LHS, true));
    TEST_DO(verify_optimized("a<@mixed", Primary::RHS, true));
}

TEST("require that all appropriate cases are optimized, others not") {
    int optimized = 0;
    for (vespalib::string lhs: {"number", "dense", "sparse", "mixed"}) {
        for (vespalib::string rhs: {"number", "dense", "sparse", "mixed"}) {
            auto expr = fmt("%s+%s", lhs.c_str(), rhs.c_str());
            TEST_STATE(expr.c_str());
            if ((lhs == "number") != (rhs == "number")) {
                auto which = (rhs == "number") ? Primary::LHS : Primary::RHS;
                verify_optimized(expr, which, false);
                ++optimized;
            } else {
                verify_not_optimized(expr);
            }
        }
    }
    EXPECT_EQUAL(optimized, 6);
}

TEST_MAIN() { TEST_RUN_ALL(); }
