# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_attributes_state_explorer_test_app TEST
    SOURCES
    attributes_state_explorer_test.cpp
    DEPENDS
    searchcore_attribute
    searchcore_pcommon
    searchcore_test
    GTest::gtest
)
vespa_add_test(NAME searchcore_attributes_state_explorer_test_app COMMAND searchcore_attributes_state_explorer_test_app)
