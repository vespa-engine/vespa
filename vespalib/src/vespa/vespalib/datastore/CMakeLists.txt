# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_datastore OBJECT
    SOURCES
    array_store.cpp
    array_store_config.cpp
    buffer_type.cpp
    bufferstate.cpp
    datastore.cpp
    datastorebase.cpp
    entryref.cpp
    fixed_size_hash_map.cpp
    simple_hash_map.cpp
    unique_store.cpp
    unique_store_string_allocator.cpp
    DEPENDS
)
