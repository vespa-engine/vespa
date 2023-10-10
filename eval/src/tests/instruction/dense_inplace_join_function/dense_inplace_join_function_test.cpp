// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

GenSpec::seq_t glb = [] (size_t) noexcept {
    static double seq_value = 0.0;
    seq_value += 1.0;
    return seq_value;
};

EvalFixture::ParamRepo make_params() {
    EvalFixture::ParamRepo repo;
    for (vespalib::string param : {
            "x5$1", "x5$2", "x5$3",
            "x5y3$1", "x5y3$2",
            "@x5$1", "@x5$2", "@x5$3",
            "@x5y3$1", "@x5y3$2",
            "@x3_1$1", "@x3_1$2"
        })
    {
        repo.add(param, CellType::DOUBLE, glb);
        repo.add(param + "_f", CellType::FLOAT, glb);
    }
    repo.add_mutable("mut_dbl_A", GenSpec(1.5));
    repo.add_mutable("mut_dbl_B", GenSpec(2.5));
    return repo;
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, size_t param_idx) {
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    for (size_t i = 0; i < fixture.num_params(); ++i) {
        TEST_STATE(vespalib::make_string("param %zu", i).c_str());
        if (i == param_idx) {
            EXPECT_EQUAL(fixture.param_value(i).cells().data,
                         fixture.result_value().cells().data);
        } else {
            EXPECT_NOT_EQUAL(fixture.param_value(i).cells().data,
                             fixture.result_value().cells().data);
        }
    }
}

void verify_p0_optimized(const vespalib::string &expr) {
    verify_optimized(expr, 0);
}

void verify_p1_optimized(const vespalib::string &expr) {
    verify_optimized(expr, 1);
}

void verify_p2_optimized(const vespalib::string &expr) {
    verify_optimized(expr, 2);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    for (size_t i = 0; i < fixture.num_params(); ++i) {
        EXPECT_NOT_EQUAL(fixture.param_value(i).cells().data,
                         fixture.result_value().cells().data);
    }
}

TEST("require that mutable dense concrete tensors are optimized") {
    TEST_DO(verify_p1_optimized("@x5$1-@x5$2"));
    TEST_DO(verify_p0_optimized("@x5$1-x5$2"));
    TEST_DO(verify_p1_optimized("x5$1-@x5$2"));
    TEST_DO(verify_p1_optimized("@x5y3$1-@x5y3$2"));
    TEST_DO(verify_p0_optimized("@x5y3$1-x5y3$2"));
    TEST_DO(verify_p1_optimized("x5y3$1-@x5y3$2"));
}

TEST("require that self-join operations can be optimized") {
    TEST_DO(verify_p0_optimized("@x5$1+@x5$1"));
}

TEST("require that join(tensor,scalar) operations are optimized") {
    TEST_DO(verify_p0_optimized("@x5$1-mut_dbl_B"));
    TEST_DO(verify_p1_optimized("mut_dbl_A-@x5$2"));
}

TEST("require that join with different tensor shapes are optimized") {
    TEST_DO(verify_p1_optimized("@x5$1*@x5y3$2"));
}

TEST("require that inplace join operations can be chained") {
    TEST_DO(verify_p2_optimized("@x5$1+(@x5$2+@x5$3)"));
    TEST_DO(verify_p0_optimized("(@x5$1+x5$2)+x5$3"));
    TEST_DO(verify_p1_optimized("x5$1+(@x5$2+x5$3)"));
    TEST_DO(verify_p2_optimized("x5$1+(x5$2+@x5$3)"));
}

TEST("require that non-mutable tensors are not optimized") {
    TEST_DO(verify_not_optimized("x5$1+x5$2"));
}

TEST("require that scalar values are not optimized") {
    TEST_DO(verify_not_optimized("mut_dbl_A+mut_dbl_B"));
    TEST_DO(verify_not_optimized("mut_dbl_A+5"));
    TEST_DO(verify_not_optimized("5+mut_dbl_B"));
}

TEST("require that mapped tensors are not optimized") {
    TEST_DO(verify_not_optimized("@x3_1$1+@x3_1$2"));
}

TEST("require that optimization works with float cells") {
    TEST_DO(verify_p1_optimized("@x5$1_f-@x5$2_f"));
}

TEST("require that overwritten value must have same cell type as result") {
    TEST_DO(verify_p0_optimized("@x5$1-@x5$2_f"));
    TEST_DO(verify_p1_optimized("@x5$2_f-@x5$1"));
    TEST_DO(verify_not_optimized("x5$1-@x5$2_f"));
    TEST_DO(verify_not_optimized("@x5$2_f-x5$1"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
