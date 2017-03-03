# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalib

    APPS
    src/apps/eval_expr

    TESTS
    src/tests/eval/aggr
    src/tests/eval/compile_cache
    src/tests/eval/compiled_function
    src/tests/eval/function
    src/tests/eval/function_speed
    src/tests/eval/gbdt
    src/tests/eval/interpreted_function
    src/tests/eval/node_types
    src/tests/eval/param_usage
    src/tests/eval/simple_tensor
    src/tests/eval/tensor_function
    src/tests/eval/value_cache
    src/tests/eval/value_type
    src/tests/tensor/dense_dot_product_function
    src/tests/tensor/dense_tensor_address_combiner
    src/tests/tensor/dense_tensor_builder
    src/tests/tensor/dense_tensor_function_compiler
    src/tests/tensor/sparse_tensor_builder
    src/tests/tensor/tensor_address
    src/tests/tensor/tensor_conformance
    src/tests/tensor/tensor_mapper
    src/tests/tensor/tensor_performance
    src/tests/tensor/tensor_serialization
    src/tests/tensor/tensor_slime_serialization

    LIBS
    src/vespa/eval
    src/vespa/eval/eval
    src/vespa/eval/eval/test
    src/vespa/eval/eval/value_cache
    src/vespa/eval/eval/llvm
    src/vespa/eval/tensor
    src/vespa/eval/tensor/sparse
    src/vespa/eval/tensor/dense
    src/vespa/eval/tensor/serialization
)
