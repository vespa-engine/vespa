# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_quant_test_app TEST
    SOURCES
    centroid_test.cpp
    hadamard_test.cpp
    DEPENDS
    vespalib
    GTest::gtest
    GTest::gmock
)
vespa_add_test(NAME vespalib_quant_test_app COMMAND vespalib_quant_test_app)
