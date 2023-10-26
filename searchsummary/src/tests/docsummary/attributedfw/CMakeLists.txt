# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_attributedfw_test_app TEST
    SOURCES
    attributedfw_test.cpp
    DEPENDS
    searchsummary
    searchsummary_test
    GTest::GTest
)
vespa_add_test(NAME searchsummary_attributedfw_test_app COMMAND searchsummary_attributedfw_test_app)
