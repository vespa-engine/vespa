# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(memfilepersistence_memfile OBJECT
    SOURCES
    memslot.cpp
    memfile.cpp
    slotiterator.cpp
    memfilecompactor.cpp
    memfilecache.cpp
    shared_data_location_tracker.cpp
    DEPENDS
)
