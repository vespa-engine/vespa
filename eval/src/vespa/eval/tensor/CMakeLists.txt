# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_tensor OBJECT
    SOURCES
    default_tensor_engine.cpp
    partial_update.cpp
    tensor.cpp
    tensor_address.cpp
    wrapped_simple_tensor.cpp
    wrapped_simple_value.cpp
)
