# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_benchmark_timer_test_app
    SOURCES
    benchmark_timer_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_benchmark_timer_test_app COMMAND vespalib_benchmark_timer_test_app BENCHMARK)
