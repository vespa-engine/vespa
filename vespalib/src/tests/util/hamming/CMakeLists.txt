# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_hamming_test_app TEST
    SOURCES
    hamming_test.cpp
    DEPENDS
    vespalib
    GTest::gtest
)
vespa_add_test(NAME vespalib_hamming_test_app COMMAND vespalib_hamming_test_app)

vespa_add_executable(vespalib_hamming_benchmark_app TEST
    SOURCES
    hamming_benchmark.cpp
    DEPENDS
    vespalib
)
