// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/instruction/fast_rename_optimizer.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("x1y5z1", GenSpec().idx("x", 1).idx("y", 5).idx("z", 1))
        .add("x1y5z1f", GenSpec().idx("x", 1).idx("y", 5).idx("z", 1).cells_float())
        .add("x1y1z1", GenSpec().idx("x", 1).idx("y", 1).idx("z", 1))
        .add("x1y5z_m", GenSpec().idx("x", 1).idx("y", 5).map("z", {"a"}));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<ReplaceTypeFunction>();
    EXPECT_EQUAL(info.size(), 1u);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<ReplaceTypeFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that dimension removal can be optimized for appropriate aggregators") {
    TEST_DO(verify_optimized("reduce(x1y5z1,avg,x)"));
    TEST_DO(verify_not_optimized("reduce(x1y5z1,count,x)")); // NB
    TEST_DO(verify_optimized("reduce(x1y5z1,prod,x)"));
    TEST_DO(verify_optimized("reduce(x1y5z1,sum,x)"));
    TEST_DO(verify_optimized("reduce(x1y5z1,max,x)"));
    TEST_DO(verify_optimized("reduce(x1y5z1,min,x)"));    
}

TEST("require that multi-dimension removal can be optimized") {
    TEST_DO(verify_optimized("reduce(x1y5z1,sum,x,z)"));
}

TEST("require that chained dimension removal can be optimized (and compacted)") {
    TEST_DO(verify_optimized("reduce(reduce(x1y5z1,sum,x),sum,z)"));
}

TEST("require that reducing non-trivial dimension is not optimized") {
    TEST_DO(verify_not_optimized("reduce(x1y5z1,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(x1y5z1,sum,x,y)"));
    TEST_DO(verify_not_optimized("reduce(x1y5z1,sum,y,z)"));
}

TEST("require that full reduce is not optimized") {
    TEST_DO(verify_not_optimized("reduce(x1y1z1,sum)"));
    TEST_DO(verify_not_optimized("reduce(x1y1z1,sum,x,y,z)"));
}

TEST("require that mixed tensor types can be optimized") {
    TEST_DO(verify_optimized("reduce(x1y5z_m,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(x1y5z_m,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(x1y5z_m,sum,z)"));
}

TEST("require that optimization works for float cells") {
    TEST_DO(verify_optimized("reduce(x1y5z1f,avg,x)"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
