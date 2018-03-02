// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_inplace_join_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/eval/eval/test/eval_fixture.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::tensor;
using namespace vespalib::eval::tensor_function;

const TensorEngine &prod_engine = DefaultTensorEngine::ref();

struct SimpleSequence : public Sequence {
    int _step, _bias;
    SimpleSequence(int step, int bias) : _step(step), _bias(bias) {}
    double operator[](size_t i) const override { return ((_step * i) + _bias) & 0xffff; }
    ~SimpleSequence() {}
};
struct SequenceGenerator {
    int step, bias;
    SequenceGenerator() : step(0), bias(0) {}
    SimpleSequence next() {
       step += 17;
       bias += 42;
       return SimpleSequence(step, bias);
    }
} seqGen;

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("con_dbl_A", spec(1.5))
        .add("con_dbl_B", spec(2.5))
        .add("con_dbl_C", spec(3.5))
        .add("con_vec_A", spec({x(5)}, seqGen.next()))
        .add("con_vec_B", spec({x(5)}, seqGen.next()))
        .add("con_vec_C", spec({x(5)}, seqGen.next()))
        .add("con_x5y3_A", spec({x(5),y(3)}, seqGen.next()))
        .add_mutable("mut_dbl_A", spec(4.5))
        .add_mutable("mut_dbl_B", spec(5.5))
        .add_mutable("mut_dbl_C", spec(6.5))
        .add_mutable("mut_vec_A", spec({x(5)}, seqGen.next()))
        .add_mutable("mut_vec_B", spec({x(5)}, seqGen.next()))
        .add_mutable("mut_vec_C", spec({x(5)}, seqGen.next()))
        .add_mutable("mut_x5y3_A", spec({x(5),y(3)}, seqGen.next()))
        .add_mutable("mut_x5y3_B", spec({x(5),y(3)}, seqGen.next()))
        .add_mutable("mut_x5y3_C", spec({x(5),y(3)}, seqGen.next()))
        .add_mutable("mut_vec_unbound", spec({x(5)}, seqGen.next()), "tensor(x[])")
        .add_mutable("mut_vec_sparse", spec({x({"a", "b", "c"})}, seqGen.next()));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, size_t cnt, size_t param_idx) {
    EvalFixture fixture(prod_engine, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.get_param(param_idx), fixture.result());
    auto info = fixture.find_all<DenseInplaceJoinFunction>();
    ASSERT_EQUAL(info.size(), cnt);
    for (size_t i = 0; i < cnt; ++i) {
        EXPECT_TRUE(info[i]->result_is_mutable());
    }
}

void verify_left_optimized(const vespalib::string &expr, size_t cnt) {
    verify_optimized(expr, cnt, 0);
}

void verify_right_optimized(const vespalib::string &expr, size_t cnt) {
    verify_optimized(expr, cnt, 1);
}

void verify_right2_optimized(const vespalib::string &expr, size_t cnt) {
    verify_optimized(expr, cnt, 2);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_engine, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_NOT_EQUAL(fixture.get_param(0), fixture.result());
    auto info = fixture.find_all<DenseInplaceJoinFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that mutable dense concrete tensors are optimized") {
    TEST_DO(verify_left_optimized("mut_vec_A-mut_vec_B", 1));
    TEST_DO(verify_left_optimized("mut_x5y3_A-mut_x5y3_B", 1));
    TEST_DO(verify_left_optimized("mut_vec_A-mut_vec_B", 1));
    TEST_DO(verify_left_optimized("mut_vec_A-con_vec_A", 1));
    TEST_DO(verify_right_optimized("con_vec_A-mut_vec_A", 1));
    TEST_DO(verify_right_optimized("con_x5y3_A-mut_x5y3_A", 1));
}

TEST("require that join(tensor,scalar) operations are not optimized (yet)") {
    TEST_DO(verify_not_optimized("mut_vec_A-mut_dbl_A"));
    TEST_DO(verify_not_optimized("mut_vec_A-con_dbl_A"));
    TEST_DO(verify_not_optimized("mut_dbl_A-mut_vec_A"));
    TEST_DO(verify_not_optimized("con_dbl_A-mut_vec_A"));
    TEST_DO(verify_not_optimized("mut_x5y3_A-mut_dbl_A"));
    TEST_DO(verify_not_optimized("mut_x5y3_A-con_dbl_A"));
    TEST_DO(verify_not_optimized("mut_dbl_A-mut_x5y3_A"));
    TEST_DO(verify_not_optimized("con_dbl_A-mut_x5y3_A"));
}

TEST("require that inplace join operations can be chained") {
    TEST_DO(verify_left_optimized("mut_vec_A-(mut_vec_B-mut_vec_C)", 2));
    TEST_DO(verify_left_optimized("(mut_vec_A-con_vec_B)-con_vec_C", 2));
    TEST_DO(verify_right_optimized("con_vec_A-(mut_vec_B-con_vec_C)", 2));
    TEST_DO(verify_right2_optimized("con_vec_A-(con_vec_B-mut_vec_C)", 2));
}

TEST("require that abstract tensors are not optimized") {
    TEST_DO(verify_not_optimized("mut_vec_unbound+mut_vec_A"));
    TEST_DO(verify_not_optimized("mut_vec_A+mut_vec_unbound"));
    TEST_DO(verify_not_optimized("mut_vec_unbound+mut_vec_unbound"));
}

TEST("require that non-mutable tensors are not optimized") {
    TEST_DO(verify_not_optimized("con_vec_A+con_vec_B"));
}

TEST("require that scalar values are not optimized") {
    TEST_DO(verify_not_optimized("mut_dbl_A+mut_dbl_B"));
    TEST_DO(verify_not_optimized("join(mut_dbl_A,mut_dbl_B,f(x,y)(x+y))"));
}

TEST("require that mapped tensors are not optimized") {
    TEST_DO(verify_not_optimized("mut_vec_sparse+mut_vec_sparse"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
