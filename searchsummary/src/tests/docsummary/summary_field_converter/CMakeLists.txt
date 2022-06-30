# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_summary_field_converter_test_app
    SOURCES
    summary_field_converter_test.cpp
    DEPENDS
    searchsummary
)
vespa_add_test(NAME searchsummary_summary_field_converter_test_app COMMAND searchsummary_summary_field_converter_test_app)
