# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# TODO: Remove test library when all tests have been migrated to gtest.
vespa_add_library(storage_testbucketdb TEST
    SOURCES
    lockablemaptest.cpp
    DEPENDS
    storage
    storage_testcommon
)

vespa_add_executable(storage_bucketdb_gtest_runner_app TEST
    SOURCES
    bucketinfotest.cpp
    bucketmanagertest.cpp
    gtest_runner.cpp
    initializertest.cpp
    judyarraytest.cpp
    judymultimaptest.cpp
    DEPENDS
    storage
    storage_testcommon
    gtest
)

vespa_add_test(
    NAME storage_bucketdb_gtest_runner_app
    COMMAND storage_bucketdb_gtest_runner_app
)
