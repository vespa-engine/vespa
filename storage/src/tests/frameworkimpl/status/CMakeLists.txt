# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(storage_status_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    htmltabletest.cpp
    statustest.cpp
    DEPENDS
    storage
    storage_testcommon
    GTest::GTest
)

vespa_add_test(
    NAME storage_status_gtest_runner_app
    COMMAND storage_status_gtest_runner_app
)
