# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_tensor OBJECT
    SOURCES
    dense_tensor_attribute.cpp
    dense_tensor_attribute_saver.cpp
    dense_tensor_store.cpp
    generic_tensor_attribute.cpp
    generic_tensor_store.cpp
    imported_tensor_attribute_vector.cpp
    tensor_attribute.cpp
    generic_tensor_attribute_saver.cpp
    tensor_store.cpp
    DEPENDS
)
