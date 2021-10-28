# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_matching_request_context_test_app TEST
    SOURCES
    request_context_test.cpp
    DEPENDS
    searchcore_matching
    GTest::GTest
)
vespa_add_test(NAME searchcore_matching_request_context_test_app COMMAND searchcore_matching_request_context_test_app)
