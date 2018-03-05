// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_fast_rename_function.h>
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

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("x5", spec({x(5)}, N()))
        .add_mutable("mut_x5", spec({x(5)}, N()))
        .add("x5_u", spec({x(5)}, N()), "tensor(x[])")
        .add("x_m", spec({x({"a", "b", "c"})}, N()))
        .add("x5y3", spec({x(5),y(3)}, N()));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, bool expect_mutable = false) {
    EvalFixture fixture(prod_engine, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseFastRenameFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_EQUAL(info[0]->result_is_mutable(), expect_mutable);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseFastRenameFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that non-transposing dense renames are optimized") {
    TEST_DO(verify_optimized("rename(x5,x,y)"));
    TEST_DO(verify_optimized("rename(x5,x,a)"));
    TEST_DO(verify_optimized("rename(x5y3,y,z)"));
    TEST_DO(verify_optimized("rename(x5y3,x,a)"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(a,b))"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(z,zz))"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(y,z))"));
    TEST_DO(verify_optimized("rename(x5y3,(y,x),(b,a))"));
}

TEST("require that transposing dense renames are not optimized") {
    TEST_DO(verify_not_optimized("rename(x5y3,x,z)"));
    TEST_DO(verify_not_optimized("rename(x5y3,y,a)"));
    TEST_DO(verify_not_optimized("rename(x5y3,(x,y),(y,x))"));
    TEST_DO(verify_not_optimized("rename(x5y3,(x,y),(b,a))"));
    TEST_DO(verify_not_optimized("rename(x5y3,(y,x),(a,b))"));
}

TEST("require that abstract dense renames are not optimized") {
    TEST_DO(verify_not_optimized("rename(x5_u,x,y)"));
}

TEST("require that non-dense renames are not optimized") {
    TEST_DO(verify_not_optimized("rename(x_m,x,y)"));
}

TEST("require that renaming a mutable result retains mutability") {
    TEST_DO(verify_optimized("rename(mut_x5,x,y)", true));
}

TEST("require that child mutability changed under-the-hood is still reflected") {
    Stash stash;
    const Node &a = inject(ValueType::from_spec("tensor(x[2])"), 0, stash);
    const Node &tmp = map(a, operation::Neg::f, stash); // will be mutable
    DenseFastRenameFunction my_rename(ValueType::from_spec("tensor(y[2])"), a);
    EXPECT_TRUE(!my_rename.result_is_mutable());
    {
        std::vector<TensorFunction::Child::CREF> list;
        my_rename.push_children(list);
        ASSERT_EQUAL(list.size(), 1u);
        EXPECT_EQUAL(&(list[0].get().get()), &a);
        const TensorFunction::Child &child = list[0];
        child.set(tmp);
    }
    EXPECT_TRUE(my_rename.result_is_mutable());
}

TEST_MAIN() { TEST_RUN_ALL(); }
