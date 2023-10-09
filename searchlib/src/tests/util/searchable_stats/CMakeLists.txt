# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_searchable_stats_test_app TEST
    SOURCES
    searchable_stats_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_searchable_stats_test_app COMMAND searchlib_searchable_stats_test_app)
