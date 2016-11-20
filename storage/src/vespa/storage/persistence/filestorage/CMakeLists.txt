# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_filestorpersistence OBJECT
    SOURCES
    filestormetrics.cpp
    filestormanager.cpp
    filestorhandler.cpp
    filestorhandlerimpl.cpp
    mergestatus.cpp
    modifiedbucketchecker.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
