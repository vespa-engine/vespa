# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespa-analyze-onnx-model
    SOURCES
    analyze_onnx_model.cpp
    INSTALL bin
    DEPENDS
    vespaeval
)
