# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_thread OBJECT
    SOURCES
    appkiller.cpp
    deadlockdetector.cpp
    htmltable.cpp
    DEPENDS
)
