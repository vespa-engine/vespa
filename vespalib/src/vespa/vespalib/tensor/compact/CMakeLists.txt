# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_tensor_compact OBJECT
    SOURCES
    compact_tensor.cpp
    compact_tensor_dimension_sum.cpp
    compact_tensor_product.cpp
    compact_tensor_v2.cpp
    compact_tensor_v2_dimension_sum.cpp
    compact_tensor_v2_match.cpp
    compact_tensor_v2_product.cpp
    compact_tensor_address.cpp
    compact_tensor_address_builder.cpp
    compact_tensor_builder.cpp
    compact_tensor_v2_builder.cpp
    compact_tensor_unsorted_address_builder.cpp
    DEPENDS
)
