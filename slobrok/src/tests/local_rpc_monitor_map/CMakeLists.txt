# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_local_rpc_monitor_map_test_app TEST
    SOURCES
    local_rpc_monitor_map_test.cpp
    DEPENDS
    slobrok_slobrokserver
    GTest::GTest
)
vespa_add_test(NAME slobrok_local_rpc_monitor_map_test_app COMMAND slobrok_local_rpc_monitor_map_test_app)
