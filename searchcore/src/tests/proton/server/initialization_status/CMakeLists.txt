# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_initialization_status_test_app TEST
    SOURCES
    initialization_status_test.cpp
    DEPENDS
    searchcore_server
    GTest::gtest
)
vespa_add_test(NAME searchcore_initialization_status_test_app COMMAND searchcore_initialization_status_test_app)
