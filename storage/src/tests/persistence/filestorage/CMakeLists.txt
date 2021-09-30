# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(storage_filestorage_gtest_runner_app TEST
    SOURCES
    deactivatebucketstest.cpp
    deletebuckettest.cpp
    filestormanagertest.cpp
    filestormodifiedbucketstest.cpp
    mergeblockingtest.cpp
    modifiedbucketcheckertest.cpp
    operationabortingtest.cpp
    sanitycheckeddeletetest.cpp
    service_layer_host_info_reporter_test.cpp
    singlebucketjointest.cpp
    gtest_runner.cpp
    DEPENDS
    storage
    storageapi
    storage_testhostreporter
    storage_testpersistence_common
    GTest::GTest
)

vespa_add_test( NAME storage_filestorage_gtest_runner_app COMMAND storage_filestorage_gtest_runner_app COST 50)
