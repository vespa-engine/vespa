# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_tensor OBJECT
    SOURCES
    default_tensor_engine.cpp
    default_value_builder_factory.cpp
    tensor.cpp
    tensor_address.cpp
    tensor_apply.cpp
    wrapped_simple_tensor.cpp
)
