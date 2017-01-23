# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_tensor_serialization OBJECT
    SOURCES
    sparse_binary_format.cpp
    dense_binary_format.cpp
    slime_binary_format.cpp
    typed_binary_format.cpp
)
