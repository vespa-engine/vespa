# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_bitvector_test-common_app TEST
    SOURCES
    bitvector_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_bitvector_test-common_app COMMAND searchlib_bitvector_test-common_app)
vespa_add_executable(searchlib_bitvector_benchmark_app
    SOURCES
    bitvector_benchmark.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_bitvector_benchmark_app COMMAND searchlib_bitvector_benchmark_app BENCHMARK)
vespa_add_executable(searchlib_condensedbitvector_test_app TEST
    SOURCES
    condensedbitvector_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_condensedbitvector_test_app COMMAND searchlib_condensedbitvector_test_app)
