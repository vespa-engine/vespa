# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_union_service_map_test_app TEST
    SOURCES
    union_service_map_test.cpp
    DEPENDS
    slobrok_slobrokserver
    GTest::GTest
)
vespa_add_test(NAME slobrok_union_service_map_test_app COMMAND slobrok_union_service_map_test_app)
