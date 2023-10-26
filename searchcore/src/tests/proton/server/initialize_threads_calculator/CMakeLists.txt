# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_initialize_threads_calculator_test_app TEST
    SOURCES
    initialize_threads_calculator_test.cpp
    DEPENDS
    searchcore_server
    GTest::GTest
)
vespa_add_test(NAME searchcore_initialize_threads_calculator_test_app COMMAND searchcore_initialize_threads_calculator_test_app)
