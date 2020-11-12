# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_library(eval_instruction OBJECT
    SOURCES
    dense_dot_product_function.cpp
    dense_lambda_peek_function.cpp
    dense_lambda_peek_optimizer.cpp
    dense_matmul_function.cpp
    dense_multi_matmul_function.cpp
    dense_simple_expand_function.cpp
    dense_tensor_peek_function.cpp
    dense_xw_product_function.cpp
    generic_concat.cpp
    generic_create.cpp
    generic_join.cpp
    generic_lambda.cpp
    generic_map.cpp
    generic_merge.cpp
    generic_peek.cpp
    generic_reduce.cpp
    generic_rename.cpp
    index_lookup_table.cpp
)
