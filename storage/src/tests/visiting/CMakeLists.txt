# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testvisiting TEST
    SOURCES
    commandqueuetest.cpp
    memory_bounded_trace_test.cpp
    visitormanagertest.cpp
    visitortest.cpp
    DEPENDS
    storage
    storage_teststorageserver
)
