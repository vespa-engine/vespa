# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(storageapi_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    storageapi_testbuckets
    storageapi_testmbusprot
    storageapi
    gtest
)

vespa_add_test(
    NAME storageapi_gtest_runner_app
    COMMAND storageapi_gtest_runner_app
)

# Runner for unit tests written in CppUnit (DEPRECATED).
vespa_add_executable(storageapi_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    storageapi_testmessageapi
    storageapi
)

vespa_add_test(
    NAME storageapi_testrunner_app
    COMMAND storageapi_testrunner_app
)
