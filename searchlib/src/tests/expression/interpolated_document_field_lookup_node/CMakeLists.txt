# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_interpolated_document_field_lookup_node_test_app
    SOURCES
    interpolated_document_field_lookup_node_test.cpp
    DEPENDS
    searchlib_test
)
vespa_add_test(NAME searchlib_interpolated_document_field_lookup_node_test_app COMMAND searchlib_interpolated_document_field_lookup_node_test_app)
