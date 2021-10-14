# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(staging_vespalib_allocinarray_test_app TEST
    SOURCES
    allocinarray_test.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_allocinarray_test_app COMMAND staging_vespalib_allocinarray_test_app)
vespa_add_executable(staging_vespalib_sort_benchmark_app
    SOURCES
    sort_benchmark.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_sort_benchmark_app COMMAND staging_vespalib_sort_benchmark_app BENCHMARK)
vespa_add_executable(staging_vespalib_allocinarray_benchmark_app
    SOURCES
    allocinarray_benchmark.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_allocinarray_benchmark_app COMMAND staging_vespalib_allocinarray_benchmark_app BENCHMARK)
