# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_partial_remove_test_app TEST
    SOURCES
    partial_remove_test.cpp
    DEPENDS
    document
    GTest::GTest
)
vespa_add_test(NAME document_partial_remove_test_app COMMAND document_partial_remove_test_app)
