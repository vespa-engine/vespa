# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(storage_bucketdb_gtest_runner_app TEST
    SOURCES
    bucketinfotest.cpp
    bucketmanagertest.cpp
    gtest_runner.cpp
    lockablemaptest.cpp
    DEPENDS
    storage
    storage_testcommon
    GTest::GTest
)

vespa_add_test(
    NAME storage_bucketdb_gtest_runner_app
    COMMAND storage_bucketdb_gtest_runner_app
)
