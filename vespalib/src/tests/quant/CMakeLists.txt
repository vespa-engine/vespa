# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_quant_test_app TEST
    SOURCES
    centroid_test.cpp
    eden_quantizer_test.cpp
    hadamard_test.cpp
    multi_bit_packer_test.cpp
    rotator_test.cpp
    sign_flip_test.cpp
    DEPENDS
    vespalib
    GTest::gtest
    GTest::gmock
)
vespa_add_test(NAME vespalib_quant_test_app COMMAND vespalib_quant_test_app)

vespa_add_executable(vespalib_quant_benchmark_app
    SOURCES
    quant_benchmarks.cpp
    DEPENDS
    vespalib
)
vespa_add_target_external_dependency(vespalib_quant_benchmark_app benchmark)
vespa_add_test(NAME vespalib_quant_benchmark_app COMMAND vespalib_quant_benchmark_app BENCHMARK)
