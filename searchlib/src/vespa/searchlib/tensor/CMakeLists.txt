# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_tensor OBJECT
    SOURCES
    generic_tensor_attribute.cpp
    generic_tensor_store.cpp
    tensor_attribute.cpp
    tensor_attribute_saver.cpp
    tensor_store.cpp
    DEPENDS
)
