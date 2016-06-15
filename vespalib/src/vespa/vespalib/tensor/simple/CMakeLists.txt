# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_tensor_simple OBJECT
    SOURCES
    simple_tensor.cpp
    simple_tensor_builder.cpp
    simple_tensor_dimension_sum.cpp
    simple_tensor_product.cpp
    DEPENDS
)
