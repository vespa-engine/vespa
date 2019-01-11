# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_datastore OBJECT
    SOURCES
    array_store_config.cpp
    buffer_type.cpp
    bufferstate.cpp
    datastore.cpp
    datastorebase.cpp
    entryref.cpp
    DEPENDS
)
