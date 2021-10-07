# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_documenttyperepo_test_app TEST
    SOURCES
    documenttyperepo_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_documenttyperepo_test_app COMMAND document_documenttyperepo_test_app)
