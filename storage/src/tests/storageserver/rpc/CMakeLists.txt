# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storage_storageserver_rpc_gtest_runner_app TEST
    SOURCES
    caching_rpc_target_resolver_test.cpp
    cluster_controller_rpc_api_service_test.cpp
    message_codec_provider_test.cpp
    storage_api_rpc_service_test.cpp
    gtest_runner.cpp
    DEPENDS
    storage_testcommon
    storage_teststorageserver
    storage
    GTest::GTest
)

vespa_add_test(
    NAME storage_storageserver_rpc_gtest_runner_app
    COMMAND storage_storageserver_rpc_gtest_runner_app
)
