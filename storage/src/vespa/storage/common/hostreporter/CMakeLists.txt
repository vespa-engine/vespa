# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_hostreporter OBJECT
    SOURCES
    cpureporter.cpp
    kernelmetrictool.cpp
    memreporter.cpp
    networkreporter.cpp
    hostinfo.cpp
    diskreporter.cpp
    versionreporter.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
