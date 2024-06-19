# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_multi_term_or_filter_search_test_app TEST
    SOURCES
    multi_term_or_filter_search_test.cpp
    DEPENDS
    vespa_searchlib
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchlib_multi_term_or_filter_search_test_app COMMAND searchlib_multi_term_or_filter_search_test_app)
