# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_atomic_test_app TEST
    SOURCES
    atomic_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_atomic_test_app COMMAND vespalib_atomic_test_app)
vespa_add_executable(vespalib_atomic_bench_app
    SOURCES
    atomic_bench.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_atomic_bench_app COMMAND vespalib_atomic_bench_app BENCHMARK)
