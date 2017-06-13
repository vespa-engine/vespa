# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testhostreporter TEST
    SOURCES
    cpureportertest.cpp
    memreportertest.cpp
    networkreportertest.cpp
    versionreportertest.cpp
    diskreportertest.cpp
    util.cpp
    hostinfotest.cpp
    DEPENDS
    storage
)
