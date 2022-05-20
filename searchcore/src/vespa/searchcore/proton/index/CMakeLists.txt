# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_index STATIC
    SOURCES
    diskindexwrapper.cpp
    index_manager_initializer.cpp
    index_writer.cpp
    indexmanager.cpp
    memoryindexwrapper.cpp
    DEPENDS
    searchcorespi
)
