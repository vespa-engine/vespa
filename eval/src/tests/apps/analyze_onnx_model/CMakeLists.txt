# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_analyze_onnx_model_test_app TEST
    SOURCES
    analyze_onnx_model_test.cpp
    DEPENDS
    vespaeval
    AFTER
    eval_analyze_onnx_model_app
)
vespa_add_test(NAME eval_analyze_onnx_model_test_app COMMAND eval_analyze_onnx_model_test_app)
