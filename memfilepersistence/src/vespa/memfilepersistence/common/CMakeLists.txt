# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(memfilepersistence_common OBJECT
    SOURCES
    environment.cpp
    options.cpp
    types.cpp
    filespecification.cpp
    exceptions.cpp
    slotmatcher.cpp
    config_lock_guard.cpp
    DEPENDS
)
