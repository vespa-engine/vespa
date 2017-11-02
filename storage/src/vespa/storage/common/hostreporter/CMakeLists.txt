# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_hostreporter OBJECT
    SOURCES
    cpureporter.cpp
    diskreporter.cpp
    hostinfo.cpp
    kernelmetrictool.cpp
    memreporter.cpp
    networkreporter.cpp
    versionreporter.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
