# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_document_field_populator_test_app TEST
    SOURCES
    document_field_populator_test.cpp
    DEPENDS
    searchcore_attribute
    searchcore_pcommon
    searchlib_test
)
vespa_add_test(NAME searchcore_document_field_populator_test_app COMMAND searchcore_document_field_populator_test_app)
