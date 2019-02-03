# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(eval_tensor_sparse OBJECT
    SOURCES
    sparse_tensor.cpp
    sparse_tensor_address_combiner.cpp
    sparse_tensor_address_padder.cpp
    sparse_tensor_address_reducer.cpp
    sparse_tensor_match.cpp
    sparse_tensor_builder.cpp
    sparse_tensor_unsorted_address_builder.cpp
)
