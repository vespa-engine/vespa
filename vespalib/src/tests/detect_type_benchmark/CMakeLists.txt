# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_detect_type_benchmark_app TEST
    SOURCES
    detect_type_benchmark.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
