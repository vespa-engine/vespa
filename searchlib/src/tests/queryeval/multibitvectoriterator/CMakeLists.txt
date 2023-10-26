# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_multibitvectoriterator_test_app TEST
    SOURCES
    multibitvectoriterator_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_multibitvectoriterator_test_app COMMAND searchlib_multibitvectoriterator_test_app)
vespa_add_executable(searchlib_multibitvectoriterator_bench_app
    SOURCES
    multibitvectoriterator_bench.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_multibitvectoriterator_bench_app COMMAND searchlib_multibitvectoriterator_bench_app and no no 10 100000000 50 50 50 BENCHMARK)
