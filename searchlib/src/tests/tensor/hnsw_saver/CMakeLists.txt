# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_hnsw_save_load_test_app TEST
    SOURCES
    hnsw_save_load_test.cpp
    DEPENDS
    searchlib_test
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_hnsw_save_load_test_app COMMAND searchlib_hnsw_save_load_test_app)
