# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_array_test_app TEST
    SOURCES
    array_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_array_test_app COMMAND vespalib_array_test_app)
vespa_add_executable(vespalib_sort_benchmark_app
    SOURCES
    sort_benchmark.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_sort_benchmark_app COMMAND vespalib_sort_benchmark_app BENCHMARK)
