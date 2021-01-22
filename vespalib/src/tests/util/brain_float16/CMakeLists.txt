# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_brain_float16_test_app TEST
    SOURCES
    brain_float16_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_brain_float16_test_app COMMAND vespalib_brain_float16_test_app)
