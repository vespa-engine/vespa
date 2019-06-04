# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# TODO: Remove test library when test has been migrated to gtest.
vespa_add_library(storage_teststatus TEST
    SOURCES
    statustest.cpp
    DEPENDS
    storage
    storage_testcommon
)

vespa_add_executable(storage_status_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    storage
    storage_testcommon
    gtest
)

vespa_add_test(
    NAME storage_status_gtest_runner_app
    COMMAND storage_status_gtest_runner_app
)
