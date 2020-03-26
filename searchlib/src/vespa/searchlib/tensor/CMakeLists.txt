# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_tensor OBJECT
    SOURCES
    default_nearest_neighbor_index_factory.cpp
    dense_tensor_attribute.cpp
    dense_tensor_attribute_saver.cpp
    dense_tensor_store.cpp
    distance_function_factory.cpp
    generic_tensor_attribute.cpp
    generic_tensor_attribute_saver.cpp
    generic_tensor_store.cpp
    hnsw_graph.cpp
    hnsw_index.cpp
    hnsw_index_loader.cpp
    hnsw_index_saver.cpp
    imported_tensor_attribute_vector.cpp
    imported_tensor_attribute_vector_read_guard.cpp
    inv_log_level_generator.cpp
    nearest_neighbor_index.cpp
    nearest_neighbor_index_saver.cpp
    tensor_attribute.cpp
    tensor_store.cpp
    DEPENDS
)
