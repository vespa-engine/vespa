# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_filter_search_test_app TEST
    SOURCES
    filter_search_test.cpp
    DEPENDS
    searchlib_queryeval_test
    vespa_searchlib
    searchlib_test
    GTest::gtest
)
vespa_add_test(NAME searchlib_filter_search_test_app COMMAND searchlib_filter_search_test_app)
