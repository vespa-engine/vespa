# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_sparse_vector_benchmark_test_app
    SOURCES
    sparse_vector_benchmark_test.cpp
    DEPENDS
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_sparse_vector_benchmark_test_app COMMAND searchlib_sparse_vector_benchmark_test_app BENCHMARK) 
