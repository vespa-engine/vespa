// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/eval/eval/test/test_io.h>

using namespace vespalib;
using namespace vespalib::eval::test;

vespalib::string module_build_path("../../../../");
vespalib::string binary = module_build_path + "src/apps/analyze_onnx_model/vespa-analyze-onnx-model";
vespalib::string probe_cmd = binary + " --probe-types";

vespalib::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}
vespalib::string source_dir = get_source_dir();
vespalib::string probe_model = source_dir + "/../../tensor/onnx_wrapper/probe_model.onnx";

//-----------------------------------------------------------------------------

TEST_F("require that output types can be probed", ServerCmd(probe_cmd, true)) {
    Slime params;
    params.setObject();
    params.get().setString("model", probe_model);
    params.get().setObject("inputs");
    params["inputs"].setString("in1", "tensor<float>(x[2],y[3])");
    params["inputs"].setString("in2", "tensor<float>(x[2],y[3])");
    Slime result = f1.invoke(params);
    EXPECT_EQUAL(result["outputs"].fields(), 3u);
    EXPECT_EQUAL(result["outputs"]["out1"].asString().make_string(), vespalib::string("tensor<float>(d0[2],d1[3])"));
    EXPECT_EQUAL(result["outputs"]["out2"].asString().make_string(), vespalib::string("tensor<float>(d0[2],d1[3])"));
    EXPECT_EQUAL(result["outputs"]["out3"].asString().make_string(), vespalib::string("tensor<float>(d0[2],d1[3])"));
}

//-----------------------------------------------------------------------------

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
