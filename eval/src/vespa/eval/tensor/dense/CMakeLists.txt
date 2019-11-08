# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_tensor_dense OBJECT
    SOURCES
    dense_add_dimension_optimizer.cpp
    dense_dimension_combiner.cpp
    dense_dot_product_function.cpp
    dense_fast_rename_optimizer.cpp
    dense_inplace_join_function.cpp
    dense_inplace_map_function.cpp
    dense_remove_dimension_optimizer.cpp
    dense_replace_type_function.cpp
    dense_tensor.cpp
    dense_tensor_address_mapper.cpp
    dense_tensor_cells_iterator.cpp
    dense_tensor_create_function.cpp
    dense_tensor_modify.cpp
    dense_tensor_reduce.cpp
    dense_tensor_view.cpp
    dense_xw_product_function.cpp
    mutable_dense_tensor_view.cpp
    typed_cells.cpp
    typed_dense_tensor_builder.cpp
    vector_from_doubles_function.cpp
)
