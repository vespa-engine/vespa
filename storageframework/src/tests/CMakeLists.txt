# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(storageframework_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    storageframework_testclock
    gtest
)

vespa_add_test(
    NAME storageframework_gtest_runner_app
    COMMAND storageframework_gtest_runner_app
)

# Runner for unit tests written in CppUnit (DEPRECATED).
vespa_add_executable(storageframework_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    storageframework_teststatus
    storageframework_testthread
)

vespa_add_test(
    NAME storageframework_testrunner_app
    COMMAND storageframework_testrunner_app
)
