# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_common_matching_elements_fields_test_app TEST
    SOURCES
    matching_elements_fields_test.cpp
    DEPENDS
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_common_matching_elements_fields_test_app COMMAND searchlib_common_matching_elements_fields_test_app)
