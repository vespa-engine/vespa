# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_attribute_transient_memory_calculator_test_app TEST
    SOURCES
    attribute_transient_memory_calculator_test.cpp
    DEPENDS
    searchcore_attribute
    searchcore_pcommon
    GTest::GTest
)
vespa_add_test(NAME searchcore_attribute_transient_memory_calculator_test_app COMMAND searchcore_attribute_transient_memory_calculator_test_app)
