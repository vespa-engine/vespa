# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(storage_visiting_gtest_runner_app TEST
    SOURCES
    commandqueuetest.cpp
    memory_bounded_trace_test.cpp
    visitormanagertest.cpp
    visitortest.cpp
    gtest_runner.cpp
    DEPENDS
    storage
    storage_teststorageserver
    GTest::GTest
)

vespa_add_test(
    NAME storage_visiting_gtest_runner_app
    COMMAND storage_visiting_gtest_runner_app
)
