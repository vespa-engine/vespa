# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_document_id_dfw_test_app TEST
    SOURCES
    document_id_dfw_test.cpp
    DEPENDS
    searchsummary
    searchsummary_test
    GTest::GTest
)
vespa_add_test(NAME searchsummary_document_id_dfw_test_app COMMAND searchsummary_document_id_dfw_test_app)
