# Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_hnsw_index_test_app TEST
    SOURCES
    hnsw_index_test.cpp
    DEPENDS
    searchlib
    gtest
)
vespa_add_test(NAME searchlib_hnsw_index_test_app COMMAND searchlib_hnsw_index_test_app)
