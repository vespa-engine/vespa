# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_small_vector_test_app TEST
    SOURCES
    small_vector_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_small_vector_test_app COMMAND vespalib_small_vector_test_app)
