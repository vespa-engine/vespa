# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_visitor OBJECT
    SOURCES
    ${CMAKE_CURRENT_BINARY_DIR}/config-stor-visitor.h
    visitor.cpp
    visitormanager.cpp
    visitormetrics.cpp
    visitorthread.cpp
    testvisitor.cpp
    recoveryvisitor.cpp
    dumpvisitor.cpp
    countvisitor.cpp
    dumpvisitorsingle.cpp
    memory_bounded_trace.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
vespa_generate_config(storage_visitor stor-visitor.def)
install(FILES stor-visitor.def DESTINATION var/db/vespa/config_server/serverdb/classes)
