# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_distance_functions_test_app TEST
    SOURCES
    distance_functions_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_distance_functions_test_app COMMAND searchlib_distance_functions_test_app)
