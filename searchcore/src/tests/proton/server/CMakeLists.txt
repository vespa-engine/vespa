# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_proton_server_vespa_test_app TEST
    SOURCES
    vespa_testrunner.cpp
    documentretriever_test.cpp
    feeddebugger_test.cpp
    feedstates_test.cpp
    health_adapter_test.cpp
    memory_flush_config_updater_test.cpp
    memoryconfigstore_test.cpp
    move_operation_limiter_test.cpp
    threading_service_config_test.cpp
    DEPENDS
    searchcore_server
    searchcore_feedoperation
    searchcore_test
)
vespa_add_test(NAME searchcore_proton_server_vespa_test_app COMMAND searchcore_proton_server_vespa_test_app)
