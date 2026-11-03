# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_summary_setup_test_app TEST
    SOURCES
    summary_setup_test.cpp
    DEPENDS
    searchcore_docsummary
    searchcore_flushengine
    searchlib_test
    GTest::gtest
)
vespa_add_test(NAME searchcore_summary_setup_test_app COMMAND searchcore_summary_setup_test_app)
