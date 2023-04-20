# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(streamingvisitors_matching_elements_filler_test_app TEST
    SOURCES
    matching_elements_filler_test.cpp
    DEPENDS
    searchlib_test
    streamingvisitors
    GTest::GTest
)
vespa_add_test(NAME streamingvisitors_matching_elements_filler_test_app COMMAND streamingvisitors_matching_elements_filler_test_app)
