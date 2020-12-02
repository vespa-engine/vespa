// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_number_join_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/tensor_model.hpp>

#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::tensor;
using namespace vespalib::eval::tensor_function;

using vespalib::make_string_short::fmt;

using Primary = DenseNumberJoinFunction::Primary;

namespace vespalib::tensor {

std::ostream &operator<<(std::ostream &os, Primary primary)
{
    switch(primary) {
    case Primary::LHS: return os << "LHS";
    case Primary::RHS: return os << "RHS";
    }
    abort();
}

}

const TensorEngine &prod_engine = DefaultTensorEngine::ref();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", spec(1.5))
        .add("number", spec(2.5))        
        .add("sparse", spec({x({"a"})}, N()))
        .add("dense", spec({y(5)}, N()))
        .add("mixed", spec({x({"a"}),y(5)}, N()))
        .add_matrix("x", 3, "y", 5);
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, Primary primary, bool inplace) {
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseNumberJoinFunction>();
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
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseNumberJoinFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that dense number join can be optimized") {
    TEST_DO(verify_optimized("x3y5+a", Primary::LHS, false));
    TEST_DO(verify_optimized("a+x3y5", Primary::RHS, false));
    TEST_DO(verify_optimized("x3y5f*a", Primary::LHS, false));
    TEST_DO(verify_optimized("a*x3y5f", Primary::RHS, false));
}

TEST("require that dense number join can be inplace") {
    TEST_DO(verify_optimized("@x3y5*a", Primary::LHS, true));
    TEST_DO(verify_optimized("a*@x3y5", Primary::RHS, true));
    TEST_DO(verify_optimized("@x3y5f+a", Primary::LHS, true));
    TEST_DO(verify_optimized("a+@x3y5f", Primary::RHS, true));
}

TEST("require that asymmetric operations work") {
    TEST_DO(verify_optimized("x3y5/a", Primary::LHS, false));
    TEST_DO(verify_optimized("a/x3y5", Primary::RHS, false));
    TEST_DO(verify_optimized("x3y5f-a", Primary::LHS, false));
    TEST_DO(verify_optimized("a-x3y5f", Primary::RHS, false));
}

TEST("require that inappropriate cases are not optimized") {
    int optimized = 0;
    for (vespalib::string lhs: {"number", "dense", "sparse", "mixed"}) {
        for (vespalib::string rhs: {"number", "dense", "sparse", "mixed"}) {
            if (((lhs == "number") && (rhs == "dense")) ||
                ((lhs == "dense") && (rhs == "number")))
            {
                ++optimized;
            } else {
                auto expr = fmt("%s+%s", lhs.c_str(), rhs.c_str());
                TEST_STATE(expr.c_str());
                verify_not_optimized(expr);
            }
        }
    }
    EXPECT_EQUAL(optimized, 2);
}

TEST_MAIN() { TEST_RUN_ALL(); }
