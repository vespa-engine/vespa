# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval
    SOURCES
    $<TARGET_OBJECTS:eval_eval>
    $<TARGET_OBJECTS:eval_eval_test>
    $<TARGET_OBJECTS:eval_eval_value_cache>
    $<TARGET_OBJECTS:eval_eval_llvm>
    $<TARGET_OBJECTS:eval_tensor>
    $<TARGET_OBJECTS:eval_tensor_sparse>
    $<TARGET_OBJECTS:eval_tensor_dense>
    $<TARGET_OBJECTS:eval_tensor_serialization>
    INSTALL lib64
    DEPENDS
    LLVM-3.4
)
