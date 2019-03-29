# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(vdslib_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    vdslib_bucketdistributiontest
    gtest
)

vespa_add_test(
    NAME vdslib_gtest_runner_app
    COMMAND vdslib_gtest_runner_app
)

# Runner for unit tests written in CppUnit (DEPRECATED).
vespa_add_executable(vdslib_testrunner_app TEST
    SOURCES
    testrunner.cpp
    DEPENDS
    vdslib_containertest
    vdslib_testdistribution
    vdslib_teststate
    vdslib_testthread
)

# TODO: Test with a larger chunk size to parallelize test suite runs 
vespa_add_test(
    NAME vdslib_testrunner_app
    COMMAND vdslib_testrunner_app
)
