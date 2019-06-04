# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# TODO: Remove test library when all tests have been migrated to gtest.
vespa_add_library(storage_testfilestorage TEST
    SOURCES
    deactivatebucketstest.cpp
    deletebuckettest.cpp
    filestormanagertest.cpp
    filestormodifiedbucketstest.cpp
    mergeblockingtest.cpp
    modifiedbucketcheckertest.cpp
    operationabortingtest.cpp
    sanitycheckeddeletetest.cpp
    singlebucketjointest.cpp
    DEPENDS
    storage
    storageapi
    storage_testpersistence_common
)

vespa_add_executable(storage_filestorage_gtest_runner_app TEST
    SOURCES
    gtest_runner.cpp
    DEPENDS
    storage
    storageapi
    storage_testpersistence_common
    gtest
)

vespa_add_test(
    NAME storage_filestorage_gtest_runner_app
    COMMAND storage_filestorage_gtest_runner_app
)
