# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_summary-feature-benchmark_app
    SOURCES
    summary-feature-benchmark.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_summary-feature-benchmark_app COMMAND vespalib_summary-feature-benchmark_app BENCHMARK)
