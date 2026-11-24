# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(streamingvisitors_single_document_matcher_test_app TEST
    SOURCES
    single_document_matcher_test.cpp
    DEPENDS
    vespa_streamingvisitors
    GTest::gtest
)
vespa_add_test(NAME streamingvisitors_single_document_matcher_test_app COMMAND streamingvisitors_single_document_matcher_test_app)
