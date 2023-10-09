# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_health_adapter_test_app TEST
    SOURCES
    health_adapter_test.cpp
    DEPENDS
    searchcore_server
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_health_adapter_test_app COMMAND searchcore_health_adapter_test_app)
