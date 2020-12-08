# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_tensor_dense OBJECT
    SOURCES
    dense_add_dimension_optimizer.cpp
    dense_fast_rename_optimizer.cpp
    dense_pow_as_map_optimizer.cpp
    dense_remove_dimension_optimizer.cpp
    dense_replace_type_function.cpp
    dense_simple_join_function.cpp
    dense_simple_map_function.cpp
    dense_single_reduce_function.cpp
    dense_tensor_create_function.cpp
    mutable_dense_tensor_view.cpp
    onnx_wrapper.cpp
    vector_from_doubles_function.cpp
)
