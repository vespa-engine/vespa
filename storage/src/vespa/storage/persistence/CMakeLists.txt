# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_spersistence OBJECT
    SOURCES
    persistenceutil.cpp
    splitbitdetector.cpp
    persistencethread.cpp
    processallhandler.cpp
    diskmoveoperationhandler.cpp
    types.cpp
    mergehandler.cpp
    bucketprocessor.cpp
    providershutdownwrapper.cpp
    bucketownershipnotifier.cpp
    fieldvisitor.cpp
    testandsethelper.cpp
    messages.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
