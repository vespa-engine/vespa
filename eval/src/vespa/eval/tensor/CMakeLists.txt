# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_tensor OBJECT
    SOURCES
    default_tensor_engine.cpp
    tensor.cpp
    tensor_address.cpp
    tensor_apply.cpp
    tensor_factory.cpp
    tensor_mapper.cpp
)
