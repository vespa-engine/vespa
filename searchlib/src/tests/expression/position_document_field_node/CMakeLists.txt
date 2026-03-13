# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_position_document_field_node_test_app
    SOURCES
    position_document_field_node_test.cpp
    DEPENDS
    searchlib_test
)
vespa_add_test(NAME searchlib_position_document_field_node_test_app COMMAND searchlib_position_document_field_node_test_app)
