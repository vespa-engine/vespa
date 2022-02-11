# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_analyze_onnx_model_app
    SOURCES
    analyze_onnx_model.cpp
    OUTPUT_NAME vespa-analyze-onnx-model
    INSTALL bin
    DEPENDS
    vespaeval
)
