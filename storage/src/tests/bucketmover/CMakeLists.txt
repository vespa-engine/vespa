# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storage_bucketmover_gtest_runner_app TEST
    SOURCES
    bucketmovertest.cpp
    htmltabletest.cpp
    gtest_runner.cpp
    DEPENDS
    storage
    storage_testcommon
    gtest
)

vespa_add_test(
    NAME storage_bucketmover_gtest_runner_app
    COMMAND storage_bucketmover_gtest_runner_app
)
