# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(memfilepersistence_spi OBJECT
    SOURCES
    memfilepersistence.cpp
    memfilepersistenceprovider.cpp
    memfilepersistenceprovidermetrics.cpp
    operationhandler.cpp
    iteratorhandler.cpp
    joinoperationhandler.cpp
    splitoperationhandler.cpp
    visitorslotmatcher.cpp
    threadlocals.cpp
    cacheevictionguard.cpp
    DEPENDS
)
