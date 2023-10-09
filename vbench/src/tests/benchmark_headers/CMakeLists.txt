# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vbench_benchmark_headers_test_app TEST
    SOURCES
    benchmark_headers_test.cpp
    DEPENDS
    vbench_test
    vbench
)
vespa_add_test(NAME vbench_benchmark_headers_test_app COMMAND vbench_benchmark_headers_test_app)
