# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(searchlib_wrappers_test_app TEST
    SOURCES
    wrappers_test.cpp
    DEPENDS
    searchlib
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchlib_wrappers_test_app COMMAND searchlib_wrappers_test_app)
