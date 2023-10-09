# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_service_map_history_test_app TEST
    SOURCES
    service_map_history_test.cpp
    DEPENDS
    slobrok_slobrokserver
    GTest::GTest
)
vespa_add_test(NAME slobrok_service_map_history_test_app COMMAND slobrok_service_map_history_test_app)
