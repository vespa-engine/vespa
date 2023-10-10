# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_distance_calculator_test_app TEST
    SOURCES
    distance_calculator_test.cpp
    DEPENDS
    searchlib
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchlib_distance_calculator_test_app COMMAND searchlib_distance_calculator_test_app)

