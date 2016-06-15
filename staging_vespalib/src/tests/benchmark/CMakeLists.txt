# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(staging_vespalib_benchmark_test_app
    SOURCES
    benchmark.cpp
    testbase.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_benchmark_test NO_VALGRIND COMMAND sh benchmark_test.sh BENCHMARK)
