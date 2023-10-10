# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_document_test_app TEST
    SOURCES
    document_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_document_test_app COMMAND document_document_test_app)
vespa_add_executable(document_fieldvalue_test_app TEST
    SOURCES
    fieldvalue_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_fieldvalue_test_app COMMAND document_fieldvalue_test_app)
vespa_add_executable(document_predicatefieldvalue_test_app TEST
    SOURCES
    predicatefieldvalue_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_predicatefieldvalue_test_app COMMAND document_predicatefieldvalue_test_app)

vespa_add_executable(document_referencefieldvalue_test_app TEST
    SOURCES
    referencefieldvalue_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_referencefieldvalue_test_app COMMAND document_referencefieldvalue_test_app)
