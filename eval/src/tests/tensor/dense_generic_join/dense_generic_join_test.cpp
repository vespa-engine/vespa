// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/dense/typed_dense_tensor_builder.h>
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

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

double seq_value = 0.0;

struct GlobalSequence : public Sequence {
    GlobalSequence() {}
    double operator[](size_t) const override {
        seq_value += 1.0;
        return seq_value;
    }
    ~GlobalSequence() {}
};
GlobalSequence seq;

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("con_x5_A",     spec({x(5)          }, seq))
        .add("con_x5y3_B",   spec({x(5),y(3)     }, seq))
        .add("con_x5z4_C",   spec({x(5),     z(4)}, seq))
        .add("con_x5y3z4_D", spec({x(5),y(3),z(4)}, seq))
        .add("con_y3_E",     spec({     y(3)     }, seq))
        .add("con_y3z4_F",   spec({     y(3),z(4)}, seq))
        .add("con_z4_G",     spec({          z(4)}, seq))
        .add("con_x5f_H",    spec({x(5)          }, seq), "tensor<float>(x[5])")
        .add("con_x5y3_I",   spec({x(5),y(3)     }, seq), "tensor<float>(x[5],y[3])")
        .add("con_x5z4_J",   spec({x(5),     z(4)}, seq), "tensor<float>(x[5],z[4])")
        .add("con_x5y3z4_K", spec({x(5),y(3),z(4)}, seq), "tensor<float>(x[5],y[3],z[4])")
        .add("con_y3_L",     spec({     y(3)     }, seq), "tensor<float>(y[3])")
        .add("con_y3z4_M",   spec({     y(3),z(4)}, seq), "tensor<float>(y[3],z[4])))")
        .add("con_z4_N",     spec({          z(4)}, seq), "tensor<float>(z[4]))")
        .add("con_y2",       spec({y(5)}, seq))
        .add("con_y2f",      spec({y(5)}, seq), "tensor<float>(y[2]))");
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_equal(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
}


TEST("require that non-overlapping dense join works") {
    TEST_DO(verify_equal("con_x5_A-con_y3_E"));
    TEST_DO(verify_equal("con_x5_A+con_y3_E"));
    TEST_DO(verify_equal("con_x5_A*con_y3_E"));

    TEST_DO(verify_equal("con_x5_A-con_y3z4_F"));
    TEST_DO(verify_equal("con_x5_A+con_y3z4_F"));
    TEST_DO(verify_equal("con_x5_A*con_y3z4_F"));

    TEST_DO(verify_equal("con_x5_A-con_z4_G"));
    TEST_DO(verify_equal("con_x5_A+con_z4_G"));
    TEST_DO(verify_equal("con_x5_A*con_z4_G"));

    TEST_DO(verify_equal("con_x5y3_B-con_z4_G"));
    TEST_DO(verify_equal("con_x5y3_B+con_z4_G"));
    TEST_DO(verify_equal("con_x5y3_B*con_z4_G"));

    TEST_DO(verify_equal("con_y3_E-con_z4_G"));
    TEST_DO(verify_equal("con_y3_E+con_z4_G"));
    TEST_DO(verify_equal("con_y3_E*con_z4_G"));
}

TEST("require that overlapping dense join works") {
    TEST_DO(verify_equal("con_x5_A-con_x5y3_B"));
    TEST_DO(verify_equal("con_x5_A+con_x5y3_B"));
    TEST_DO(verify_equal("con_x5_A*con_x5y3_B"));

    TEST_DO(verify_equal("con_x5_A-con_x5z4_C"));
    TEST_DO(verify_equal("con_x5_A+con_x5z4_C"));
    TEST_DO(verify_equal("con_x5_A*con_x5z4_C"));

    TEST_DO(verify_equal("con_x5y3_B-con_y3_E"));
    TEST_DO(verify_equal("con_x5y3_B+con_y3_E"));
    TEST_DO(verify_equal("con_x5y3_B*con_y3_E"));

    TEST_DO(verify_equal("con_x5y3_B-con_y3z4_F"));
    TEST_DO(verify_equal("con_x5y3_B+con_y3z4_F"));
    TEST_DO(verify_equal("con_x5y3_B*con_y3z4_F"));

    TEST_DO(verify_equal("con_x5y3z4_D-con_x5y3_B"));
    TEST_DO(verify_equal("con_x5y3z4_D+con_x5y3_B"));
    TEST_DO(verify_equal("con_x5y3z4_D*con_x5y3_B"));

    TEST_DO(verify_equal("con_x5y3z4_D-con_x5z4_C"));
    TEST_DO(verify_equal("con_x5y3z4_D+con_x5z4_C"));
    TEST_DO(verify_equal("con_x5y3z4_D*con_x5z4_C"));

    TEST_DO(verify_equal("con_x5y3z4_D-con_y3z4_F"));
    TEST_DO(verify_equal("con_x5y3z4_D+con_y3z4_F"));
    TEST_DO(verify_equal("con_x5y3z4_D*con_y3z4_F"));

    TEST_DO(verify_equal("con_x5y3z4_D-con_y3z4_F"));
    TEST_DO(verify_equal("con_x5y3z4_D+con_y3z4_F"));
    TEST_DO(verify_equal("con_x5y3z4_D*con_y3z4_F"));

    TEST_DO(verify_equal("con_y3_E-con_y3z4_F"));
    TEST_DO(verify_equal("con_y3_E+con_y3z4_F"));
    TEST_DO(verify_equal("con_y3_E*con_y3z4_F"));

    TEST_DO(verify_equal("con_y3z4_F-con_z4_G"));
    TEST_DO(verify_equal("con_y3z4_F+con_z4_G"));
    TEST_DO(verify_equal("con_y3z4_F*con_z4_G"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
