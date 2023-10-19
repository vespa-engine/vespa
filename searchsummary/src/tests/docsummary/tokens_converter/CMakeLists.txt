# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_tokens_converter_test_app TEST
    SOURCES
    tokens_converter_test.cpp
    DEPENDS
    searchsummary
    GTest::gtest
)

vespa_add_test(NAME searchsummary_tokens_converter_test_app COMMAND searchsummary_tokens_converter_test_app)
