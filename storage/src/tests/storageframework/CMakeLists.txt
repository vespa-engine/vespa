# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(storageframework_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    storageframework_testclock
    storageframework_testthread
    GTest::GTest
)

vespa_add_test(
    NAME storageframework_gtest_runner_app
    COMMAND storageframework_gtest_runner_app
)
