# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(storageapi_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    storageapi_testbuckets
    storageapi_testmbusprot
    storageapi_testmessageapi
    storage
    GTest::GTest
)

vespa_add_test(
    NAME storageapi_gtest_runner_app
    COMMAND storageapi_gtest_runner_app
)

