# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_nice_test_app TEST
    SOURCES
    nice_test.cpp
    DEPENDS
    vespalib
    GTest::gtest
)
