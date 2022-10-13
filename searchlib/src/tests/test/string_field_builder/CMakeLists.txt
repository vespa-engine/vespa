# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_string_field_builder_test_app TEST
    SOURCES
    string_field_builder_test.cpp
    DEPENDS
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchlib_string_field_builder_test_app COMMAND searchlib_string_field_builder_test_app)
