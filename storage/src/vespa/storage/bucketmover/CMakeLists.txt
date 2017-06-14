# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_bucketmover OBJECT
    SOURCES
    htmltable.cpp
    move.cpp
    runstatistics.cpp
    run.cpp
    bucketmover.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
