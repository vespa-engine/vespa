# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_tensor_dense OBJECT
    SOURCES
    direct_dense_tensor_builder.cpp
    dense_dot_product_function.cpp
    dense_xw_product_function.cpp
    dense_tensor.cpp
    dense_tensor_address_combiner.cpp
    dense_tensor_builder.cpp
    dense_tensor_cells_iterator.cpp
    dense_tensor_function_compiler.cpp
    dense_tensor_view.cpp
    dense_tensor_reduce.cpp
    mutable_dense_tensor_view.cpp
)
