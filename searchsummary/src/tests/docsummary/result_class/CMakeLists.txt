# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_result_class_test_app TEST
    SOURCES
    result_class_test.cpp
    DEPENDS
    searchsummary
    GTest::GTest
)
vespa_add_test(NAME searchsummary_result_class_test_app COMMAND searchsummary_result_class_test_app)
