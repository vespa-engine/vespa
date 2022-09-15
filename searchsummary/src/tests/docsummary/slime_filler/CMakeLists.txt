# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_slime_filler_test_app TEST
    SOURCES
    slime_filler_test.cpp
    DEPENDS
    searchsummary
    GTest::GTest
)
vespa_add_test(NAME searchsummary_slime_filler_test_app COMMAND searchsummary_slime_filler_test_app)
