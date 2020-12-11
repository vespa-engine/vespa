# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespaeval
    SOURCES
    $<TARGET_OBJECTS:eval_eval>
    $<TARGET_OBJECTS:eval_eval_llvm>
    $<TARGET_OBJECTS:eval_eval_test>
    $<TARGET_OBJECTS:eval_eval_value_cache>
    $<TARGET_OBJECTS:eval_gp>
    $<TARGET_OBJECTS:eval_instruction>
    $<TARGET_OBJECTS:eval_onnx>
    $<TARGET_OBJECTS:eval_streamed>
    INSTALL lib64
    DEPENDS
    onnxruntime
    ${VESPA_LLVM_LIB}
)

set(BLA_VENDOR OpenBLAS)
vespa_add_target_package_dependency(vespaeval BLAS)
