# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_select_test_app TEST
    SOURCES
    select_test.cpp
    DEPENDS
    document
    GTest::GTest
)
vespa_add_test(NAME document_select_test_app COMMAND document_select_test_app)
