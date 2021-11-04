# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_searchlib_test_memoryindex
    SOURCES
    mock_field_index_collection.cpp
    ordered_field_index_inserter.cpp
    ordered_field_index_inserter_backend.cpp
    DEPENDS
)
