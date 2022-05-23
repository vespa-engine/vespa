# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_clock_benchmark_app TEST
    SOURCES
    clock_benchmark.cpp
    DEPENDS
    vespalib
)
vespa_add_executable(vespalib_clock_test_app TEST
    SOURCES
    clock_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_clock_test_app COMMAND vespalib_clock_test_app)
