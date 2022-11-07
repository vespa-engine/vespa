# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_generator_test_app TEST
    SOURCES
    generator_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_executable(vespalib_generator_bench_app TEST
    SOURCES
    generator_bench.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_generator_test_app COMMAND vespalib_generator_test_app)
