# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_barrier_test_app TEST
    SOURCES
    barrier_test.cpp
    DEPENDS
    vespalib
    GTest::gtest
)
vespa_add_test(NAME vespalib_barrier_test_app COMMAND vespalib_barrier_test_app)
