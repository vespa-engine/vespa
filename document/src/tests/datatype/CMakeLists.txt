# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(document_datatype_test_app TEST
    SOURCES
    datatype_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_datatype_test_app COMMAND document_datatype_test_app)

vespa_add_executable(document_referencedatatype_test_app TEST
    SOURCES
    referencedatatype_test.cpp
    DEPENDS
    document
)
vespa_add_test(NAME document_referencedatatype_test_app COMMAND document_referencedatatype_test_app)
