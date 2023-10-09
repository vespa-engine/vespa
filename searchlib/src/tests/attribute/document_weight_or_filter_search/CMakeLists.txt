# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_document_weight_or_filter_search_test_app TEST
    SOURCES
    document_weight_or_filter_search_test.cpp
    DEPENDS
    searchlib
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchlib_document_weight_or_filter_search_test_app COMMAND searchlib_document_weight_or_filter_search_test_app)
