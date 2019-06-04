# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# TODO: Remove test library when all tests have been migrated to gtest.
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

vespa_add_executable(storage_visiting_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    storage
    storage_teststorageserver
    gtest
)

vespa_add_test(
    NAME storage_visiting_gtest_runner_app
    COMMAND storage_visiting_gtest_runner_app
)
