# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_test_executable(searchlib_filter_predicate_node_test_app
    SOURCES
    filter_predicate_node_test.cpp
    DEPENDS
    searchlib_test
)
