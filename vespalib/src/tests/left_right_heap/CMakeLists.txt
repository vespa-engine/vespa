# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_left_right_heap_test_app TEST
    SOURCES
    left_right_heap_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_left_right_heap_test_app COMMAND vespalib_left_right_heap_test_app COST 30)
vespa_add_executable(vespalib_left_right_heap_bench_app
    SOURCES
    left_right_heap_bench.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_left_right_heap_bench_app COMMAND vespalib_left_right_heap_bench_app BENCHMARK)
