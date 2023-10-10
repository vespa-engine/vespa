# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_weak_and_test_app TEST
    SOURCES
    weak_and_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_weak_and_test_app COMMAND searchlib_weak_and_test_app)
vespa_add_executable(searchlib_weak_and_test_expensive_app TEST
    SOURCES
    weak_and_test_expensive.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_weak_and_test_expensive_app NO_VALGRIND COMMAND searchlib_weak_and_test_expensive_app)
vespa_add_executable(searchlib_weak_and_bench_app
    SOURCES
    weak_and_bench.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_weak_and_bench_app COMMAND searchlib_weak_and_bench_app BENCHMARK)
vespa_add_executable(searchlib_parallel_weak_and_bench_app
    SOURCES
    parallel_weak_and_bench.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_parallel_weak_and_bench_app COMMAND searchlib_parallel_weak_and_bench_app BENCHMARK)
