# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_tensor
    SOURCES
    default_tensor_engine.cpp
    tensor.cpp
    tensor_address.cpp
    tensor_apply.cpp
    tensor_factory.cpp
    tensor_mapper.cpp
    $<TARGET_OBJECTS:vespalib_vespalib_tensor_sparse>
    $<TARGET_OBJECTS:vespalib_vespalib_tensor_dense>
    $<TARGET_OBJECTS:vespalib_vespalib_tensor_serialization>
    INSTALL lib64
    DEPENDS
    vespalib
)
