# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_gencnt_test_app TEST
    SOURCES
    gencnt_test.cpp
    DEPENDS
    vespalib
    GTest::gtest
)
vespa_add_test(NAME vespalib_gencnt_test_app COMMAND vespalib_gencnt_test_app)
