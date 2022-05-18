# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_teststorageserver TEST
    SOURCES
    testvisitormessagesession.cpp
    DEPENDS
    storage_testcommon
    storage
)

vespa_add_executable(storage_storageserver_gtest_runner_app TEST
    SOURCES
    bouncertest.cpp
    changedbucketownershiphandlertest.cpp
    communicationmanagertest.cpp
    configurable_bucket_resolver_test.cpp
    documentapiconvertertest.cpp
    mergethrottlertest.cpp
    priorityconvertertest.cpp
    service_layer_error_listener_test.cpp
    statemanagertest.cpp
    statereportertest.cpp
    gtest_runner.cpp
    DEPENDS
    storage_testcommon
    storage_teststorageserver
    storage
    GTest::GTest
)

vespa_add_test(
    NAME storage_storageserver_gtest_runner_app
    COMMAND storage_storageserver_gtest_runner_app
    COST 300
)
