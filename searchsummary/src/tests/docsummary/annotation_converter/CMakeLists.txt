# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_annotation_converter_test_app TEST
    SOURCES
    annotation_converter_test.cpp
    DEPENDS
    searchsummary
    GTest::GTest
)
vespa_add_test(NAME searchsummary_annotation_converter_test_app COMMAND searchsummary_annotation_converter_test_app)
